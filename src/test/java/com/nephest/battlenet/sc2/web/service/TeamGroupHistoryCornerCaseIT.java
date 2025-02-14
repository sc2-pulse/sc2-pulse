// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.HistoryColumn;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
public class TeamGroupHistoryCornerCaseIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired @Qualifier("minimalConversionService")
    private ConversionService minConversionService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
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
    public void whenTeamSnapshotsOverstepCurrentSeasonBoundaries_thenIgnoreBoundaries()
    throws Exception
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);

        List<Season> seasons = new ArrayList<>();
        for(int i = 0; i < 2; i++)
            seasons.add(new Season(null, i + 1, Region.EU, 2020, i,
                start.plusDays(i), start.plusDays(i + 1)));
        seasonGenerator.generateSeason
        (
            seasons,
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );

        jdbcTemplate.update("DELETE FROM team_state");
        OffsetDateTime oversteppedOdt = seasons.get(1).getEnd().plusMinutes(1);
        teamStateDAO.takeSnapshot(List.of(2L), oversteppedOdt);

        List<TeamHistory> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history")
                .queryParam("teamId", "2")
                .queryParam
                (
                    "history",
                    mvcConversionService.convert(HistoryColumn.TIMESTAMP, String.class)
                )
                .queryParam
                (
                    "from",
                    mvcConversionService.convert(oversteppedOdt, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.TIMESTAMP")
            .isEqualTo(List.of(
                new TeamHistory
                (
                    Map.of(),
                    Map.ofEntries
                    (
                        entry
                        (
                            HistoryColumn.TIMESTAMP,
                            List.of(oversteppedOdt.toEpochSecond())
                        )
                    )
                )
            ));
    }

    @Test
    public void testGamesSummaryReset()
    throws Exception
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);

        List<Season> seasons = new ArrayList<>();
        for(int i = 0; i < 2; i++)
            seasons.add(new Season(null, i + 1, Region.EU, 2020, i,
                start.plusDays(i), start.plusDays(i + 1)));
        seasonGenerator.generateSeason
        (
            seasons,
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );

        jdbcTemplate.update("DELETE FROM team_state");
        jdbcTemplate.update("UPDATE team SET last_played = null, legacy_id = 200");
        //team starts with 3 games, but it's counted as 1 game because it's the first snapshot
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart());
        //nothing has changed, 0 games
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(1));


        Team team1 = teamDAO.findById(1L).orElseThrow();
        Team team2 = teamDAO.findById(2L).orElseThrow();

        //bump games for the merge, this should have no effect expect for allowing us to merge
        team1.setWins(team1.getWins() -1);
        teamDAO.merge(Set.of(team1));
        team1.setWins(team1.getWins() + 1);
        //3 games because games didn't change but rating did, which means there was a reset
        team1.setRating(team2.getRating());
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(2));

        //team 2 has 6 games, update to 6 games, so it's 6-3=3 games
        team1.setWins(team2.getWins());
        team1.setLosses(team2.getLosses());
        team1.setTies(team2.getTies());
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(3));

        //6 games in the snapshot, but it's the first snapshot in a new season, so it's counted
        //as 6 games
        teamStateDAO.takeSnapshot(List.of(2L), seasons.get(1).getStart());

        List<TeamHistorySummary> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history/summary")
                .queryParam
                (
                    "legacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            Region.EU,
                            "200"
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "summary",
                    mvcConversionService.convert(TeamHistoryDAO.SummaryColumn.GAMES, String.class)
                )
                .queryParam
                (
                    "groupBy",
                    mvcConversionService.convert(TeamHistoryDAO.GroupMode.LEGACY_UID, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(List.of(new TeamHistorySummary(
                Map.of(),
                Map.of(TeamHistoryDAO.SummaryColumn.GAMES, 13) //1 + 0 + 3 + 3 + 6
            )));
    }

    @Test
    public void whenNotPlayerAction_thenIgnoreSuchSnapshotsInSummary()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason(1);
        jdbcTemplate.update("UPDATE team SET last_played = null");
        Team team1 = teamDAO.findById(1L).orElseThrow();
        //nothing has changed, tech snapshot
        teamStateDAO.takeSnapshot(List.of(1L), SeasonGenerator.DEFAULT_SEASON_START.plusHours(1));
        //the data has changed, player action
        team1.setRating(1L);
        team1.setWins(team1.getWins() + 1);
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), SeasonGenerator.DEFAULT_SEASON_START.plusHours(2));

        List<TeamHistorySummary> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history/summary")
                .queryParam("teamId", "1")
                .queryParam
                (
                    "summary",
                    mvcConversionService.convert(TeamHistoryDAO.SummaryColumn.RATING_AVG, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        //the technical snapshot is ignored, so 1 rating(0 + 1) is divided by 2 instead of 3
        assertEquals(0.5d, found.get(0).summary().get(TeamHistoryDAO.SummaryColumn.RATING_AVG));
    }

}
