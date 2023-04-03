// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.filter.NoCacheFilter;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ExternalServiceIT
{

    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE =
        BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private PlayerCharacterLinkDAO playerCharacterLinkDAO;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private SC2ArcadeAPI arcadeAPI;

    @Autowired
    private Environment environment;

    private MockMvc mvc;
    private PlayerCharacter character;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        cacheManager.getCacheNames()
            .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
        init();
    }

    private void init()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 2, region, 2020, 2,
            LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1));
        seasonGenerator.generateSeason
        (
            List.of(season1),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TEAM_TYPE,
            TIER_TYPE,
            3
        );
        Division bronze1v1 = divisionDAO.findListByLadder
        (
            season1.getBattlenetId(),
            region,
            BaseLeague.LeagueType.BRONZE,
            QueueType.LOTV_1V1,
            TEAM_TYPE,
            TIER_TYPE
        ).get(0);

        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        character = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), region, 315071L, 1, "name#1"));
        seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE),
            TIER_TYPE, bronze1v1,
            BigInteger.valueOf(10002L), 1L, 1, 2, 3, 4,
            character
        );
        playerCharacterStatsDAO.mergeCalculate();
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testExternalCharacterSearchByBattleNetProfile()
    throws Exception
    {
        verifyExternalCharacterSearchByBattleNetProfile();
        cacheManager.getCacheNames()
            .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        verifyExternalCharacterSearchByBattleNetProfile();
        verifyExternalLinkResolver();
        /*
            The API was called once despite several searches was run. Previous search results
            should be persisted in the DB and used when possible to avoid redundant API calls.
         */
        verify(arcadeAPI, times(1)).findByRegionAndGameId(any(), anyLong());
    }

    @Test
    public void testExternalLinkResolver() throws Exception
    {
        verifyExternalLinkResolver();
        cacheManager.getCacheNames()
            .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        verifyExternalLinkResolver();
        verifyExternalCharacterSearchByBattleNetProfile();

        /*
            The API was called once despite several searches was run. Previous search results
            should be persisted in the DB and used when possible to avoid redundant API calls.
         */
        verifyDbLinks();
        verify(arcadeAPI, times(1)).findCharacter(any());
    }

    @Test
    public void whenExternalLinksResolverFails_thenAddFailedType()
    throws Exception
    {
        doReturn(Mono.error(new WebClientResponseException(500, "ISE", null, null, null)))
            .when(arcadeAPI).findCharacter(any());
        ExternalLinkResolveResult result = objectMapper.readValue(mvc.perform
        (
            get("/api/character/{id}/links/additional", character.getId())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is5xxServerError())
            .andExpect(header().string
            (
                HttpHeaders.CACHE_CONTROL,
                NoCacheFilter.NO_CACHE_HEADERS.get(HttpHeaders.CACHE_CONTROL)
            ))
            .andReturn().getResponse().getContentAsString(), ExternalLinkResolveResult.class);
        assertTrue(result.getFailedTypes().contains(SocialMedia.BATTLE_NET));
    }

    private void verifyExternalCharacterSearchByBattleNetProfile()
    throws Exception
    {
        LadderDistinctCharacter[] characterFound = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search")
                .queryParam("term", "battlenet:://starcraft/profile/2/4771787010354446336")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderDistinctCharacter[].class);
        assertEquals(1, characterFound.length);
        assertEquals(character, characterFound[0].getMembers().getCharacter());
    }

    private void verifyExternalLinkResolver()
    throws Exception
    {
        ExternalLinkResolveResult result = objectMapper.readValue(mvc.perform
        (
            get("/api/character/{id}/links/additional", character.getId())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andExpect(header().string
            (
                HttpHeaders.CACHE_CONTROL,
                WebServiceUtil.DEFAULT_CACHE_HEADER
            ))
            .andReturn().getResponse().getContentAsString(), ExternalLinkResolveResult.class);
        assertTrue(result.getFailedTypes().isEmpty());
        verifyLinks(result.getLinks());
    }

    private void verifyDbLinks()
    {
        verifyLinks(playerCharacterLinkDAO.find(character.getId()));
    }

    private void verifyLinks(List<PlayerCharacterLink> links)
    {
        assertFalse(links.isEmpty());
        verifyLink
        (
            links,
            SocialMedia.BATTLE_NET,
            "battlenet:://starcraft/profile/2/4771787010354446336"
        );
        if(isReplayStatsEnabled()) verifyLink
        (
            links,
            SocialMedia.REPLAY_STATS,
            "https://sc2replaystats.com/player/125470"
        );
    }

    private PlayerCharacterLink find(List<PlayerCharacterLink> links, SocialMedia type)
    {
        return links.stream()
            .filter(l->l.getType() == type)
            .findAny()
            .orElseThrow();
    }

    private void verifyLink(PlayerCharacterLink link, SocialMedia type, String absoluteUrl)
    {
        assertEquals(character.getId(), link.getPlayerCharacterId());
        assertEquals(type, link.getType());
        assertEquals(absoluteUrl, link.getAbsoluteUrl());
    }

    private void verifyLink(List<PlayerCharacterLink> links, SocialMedia type, String absoluteUrl)
    {
        verifyLink(find(links, type), type, absoluteUrl);
    }


    private boolean isReplayStatsEnabled()
    {
        return environment.getProperty("com.nephest.battlenet.sc2.replaystats.api.key") != null;
    }

}
