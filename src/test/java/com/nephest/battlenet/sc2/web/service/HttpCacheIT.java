// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.web.util.StatefulRestTemplateInterceptor;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
(
    classes = AllTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class HttpCacheIT
{

    private static final Pattern NON_ZERO_CACHE_PATTERN = Pattern.compile(".*max-age=[^0].*");

    @LocalServerPort
    private int port;

    @Autowired
    private SeasonDAO seasonDAO;

    private TestRestTemplate restTemplate;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired CacheManager cacheManager,
        @Autowired SeasonGenerator generator,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired PopulationStateDAO populationStateDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        }

        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = start.plusMonths(2);
        generator.generateSeason
        (
            Arrays.stream(Region.values())
                .map(r-> new Season(null, 1, r, 2020, 1, start, end))
                .collect(Collectors.toList()),
            List.of(BaseLeague.LeagueType.values()),
            new ArrayList<>(QueueType.getTypes(StatsService.VERSION)),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(List.of(SeasonGenerator.DEFAULT_SEASON_ID));
    }

    @BeforeEach
    public void beforeEach()
    {
        restTemplate = new TestRestTemplate();
        restTemplate.getRestTemplate().setInterceptors(List.of(new StatefulRestTemplateInterceptor()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/season/list/all",
        "/api/ladder/stats/bundle"
    })
    public void testCacheSecurity(String path)
    {
        //csrf cookie is expected here, don't cache it
        ResponseEntity<String> responseNoCache = restTemplate.exchange
        (
            "http://localhost:" + port + path,
            HttpMethod.GET,
            new HttpEntity<>(null, null),
            String.class
        );
        verifyNoCacheHeaders(responseNoCache.getHeaders());

        //secure response, cache it
        ResponseEntity<String> responseCache = restTemplate.exchange
        (
            "http://localhost:" + port + path,
            HttpMethod.GET,
            new HttpEntity<>(null, null),
            String.class
        );
        assertTrue(NON_ZERO_CACHE_PATTERN
            .matcher(responseCache.getHeaders().get(HttpHeaders.CACHE_CONTROL).get(0))
            .matches()
        );
    }

    public static void verifyNoCacheHeaders(HttpHeaders headers)
    {
        NoCacheFilter.NO_CACHE_HEADERS.forEach((key, value)->
        {
            List<String> vals = headers.get(key);
            assertEquals(1, vals.size());
            assertEquals(value, vals.get(0));
        });
    }

}
