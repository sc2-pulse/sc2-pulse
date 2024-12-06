// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.controller.TeamController;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class RecentTeamIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static ConversionService cs;
    private static JdbcTemplate jdbc;
    private static String urlStart1v1;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired JdbcTemplate jdbc,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired @Qualifier("mvcConversionService") ConversionService cs
    )
    throws SQLException
    {
        RecentTeamIT.cs = cs;
        RecentTeamIT.jdbc = jdbc;
        urlStart1v1 = "/api/team?queue=" + cs.convert(QueueType.LOTV_1V1, String.class)
            + "&league=" + cs.convert(BaseLeague.LeagueType.GOLD, String.class);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU, Region.US),
            List.of
            (
                BaseLeague.LeagueType.BRONZE,
                BaseLeague.LeagueType.GOLD,
                BaseLeague.LeagueType.DIAMOND
            ),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            40,
            true
        );
        jdbc.update("UPDATE team SET wins = id");
        jdbc.update("UPDATE team SET rating = wins * 2");
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    public static Stream<Arguments> testFindRecentTeams()
    {
        jdbc.update
        (
            """
                UPDATE team SET last_played = NOW()
                - INTERVAL '%1$s seconds'
                + make_interval(secs=>team.id / 10000.0)
            """.formatted(TeamController.RECENT_TEAMS_OFFSET.minusSeconds(10).toSeconds())
        );
        String head1v1rr = urlStart1v1 + "&region=" + cs.convert(Region.EU, String.class)
            + "&race=" + cs.convert(Race.PROTOSS, String.class);
        return Stream.of
        (
            Arguments.of
            (
                head1v1rr,
                new Long[]{82L, 86L, 90L, 94L, 98L, 102L, 106L, 110L, 114L, 118L}
            ),
            Arguments.of
            (
                head1v1rr
                    + "&ratingMin=172&ratingMax=228"
                    + "&winsMin=82&winsMax=110"
                    + "&limit=5",
                new Long[]{94L, 98L, 102L, 106L, 110L}
            ),

            Arguments.of
            (
                head1v1rr + "&winsMin=106",
                new Long[]{106L, 110L, 114L, 118L}
            ),
            Arguments.of
            (
                head1v1rr + "&winsMax=82",
                new Long[]{82L}
            ),

            Arguments.of
            (
                head1v1rr + "&ratingMin=212",
                new Long[]{106L, 110L, 114L, 118L}
            ),
            Arguments.of
            (
                head1v1rr + "&ratingMax=164",
                new Long[]{82L}
            )
        );
    }

    public static Stream<Arguments> testBadRequest()
    {
        return Stream.of
        (
            Arguments.of(urlStart1v1 + "&winsMin=10&winsMax=9"),
            Arguments.of(urlStart1v1 + "&winsMin=-1"),
            Arguments.of(urlStart1v1 + "&winsMax=-1"),
            Arguments.of(urlStart1v1 + "&ratingMin=10&ratingMax=9"),
            Arguments.of(urlStart1v1 + "&ratingMin=-1"),
            Arguments.of(urlStart1v1 + "&ratingMax=-1"),
            Arguments.of(urlStart1v1 + "&limit=-1"),
            Arguments.of(urlStart1v1 + "&limit=" + (TeamController.RECENT_TEAMS_LIMIT + 1)),
            Arguments.of
            (
                "/api/team?queue=" + cs.convert(QueueType.LOTV_2V2, String.class)
                    + "&league=" + cs.convert(BaseLeague.LeagueType.GOLD, String.class)
                    + "?race=" + cs.convert(Race.TERRAN, String.class)
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFindRecentTeams(String url, Long[] teamIds)
    throws Exception
    {

        Long[] foundIds = Arrays.stream(objectMapper.readValue(mvc.perform
        (
            get(url)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderTeam[].class))
            .map(LadderTeam::getId)
            .sorted()
            .toArray(Long[]::new);
        assertArrayEquals(teamIds, foundIds);
    }

    @MethodSource
    @ParameterizedTest
    public void testBadRequest(String url)
    throws Exception
    {
        mvc.perform
        (
            get(url)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenTeamIsTooOld_thenSkipIt()
    throws Exception
    {
        jdbc.update
        (
            "UPDATE team SET last_played = ?",
            SC2Pulse.offsetDateTime().minus(TeamController.RECENT_TEAMS_OFFSET).minusSeconds(1)
        );
        mvc.perform
        (
            get(urlStart1v1)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    public void whenSearchingForMultiMemberTeam_thenGetAllMembers()
    throws Exception
    {
        jdbc.update
        (
            "UPDATE team SET last_played = ?",
            SC2Pulse.offsetDateTime().minus(TeamController.RECENT_TEAMS_OFFSET).plusSeconds(10)
        );

        LadderTeam[] teams = objectMapper.readValue(mvc.perform
        (
            get
            (
                "/api/team?queue=" + cs.convert(QueueType.LOTV_2V2, String.class)
                    + "&league=" + cs.convert(BaseLeague.LeagueType.BRONZE, String.class)
                    + "&limit=1"
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderTeam[].class);
        assertEquals(2, teams[0].getMembers().size());
    }

}
