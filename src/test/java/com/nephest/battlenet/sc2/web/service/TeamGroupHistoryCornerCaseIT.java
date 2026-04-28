// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.HistoryColumn;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickhouse.client.api.Client;
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
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryHistoryData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.util.ClickHouseUtil;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
public class TeamGroupHistoryCornerCaseIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private TeamHistoryDAO teamHistoryDAO;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private ClickHouseUtil clickHouseUtil;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
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
        teamHistoryDAO.trySync();

        Team team = teamDAO.findById(2L).orElseThrow();
        String legacyUidString = mvcConversionService.convert(team.getLegacyUid(), String.class);
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                .queryParam("teamLegacyUid", legacyUidString)
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
        ).andExpect(request().asyncStarted()).andReturn()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    new RawTeamHistoryStaticData(Map.of(
                        TeamHistoryDAO.StaticColumn.LEGACY_UID,
                        legacyUidString
                    )),
                    new RawTeamHistoryHistoryData(Map.ofEntries(
                        entry
                        (
                            HistoryColumn.TIMESTAMP,
                            List.of(oversteppedOdt.toEpochSecond())
                        )
                    ))
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
        jdbcTemplate.update("UPDATE team SET last_played = null, legacy_id = '1.200.1'");
        //team starts with 3 games, but it's counted as 1 game because it's the first snapshot
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart());
        //nothing has changed, 0 games
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(1));


        Team team1 = teamDAO.findById(1L).orElseThrow();
        Team team2 = teamDAO.findById(2L).orElseThrow();

        //bump games for the merge, this should have no effect expect for allowing us to merge
        team1.setWins(team1.getWins() -1);
        team1.setPrimaryDataUpdated(team1.getPrimaryDataUpdated().plusSeconds(1));
        teamDAO.merge(Set.of(team1));
        team1.setWins(team1.getWins() + 1);
        //3 games because games didn't change but rating did, which means there was a reset
        team1.setRating(team2.getRating());
        team1.setPrimaryDataUpdated(team1.getPrimaryDataUpdated().plusSeconds(1));
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(2));

        //team 2 has 6 games, update to 6 games, so it's 6-3=3 games
        team1.setWins(team2.getWins());
        team1.setLosses(team2.getLosses());
        team1.setTies(team2.getTies());
        team1.setPrimaryDataUpdated(team1.getPrimaryDataUpdated().plusSeconds(1));
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), seasons.get(0).getStart().plusHours(3));

        //6 games in the snapshot, but it's the first snapshot in a new season, so it's counted
        //as 6 games
        teamStateDAO.takeSnapshot(List.of(2L), seasons.get(1).getStart());

        teamHistoryDAO.trySync();

        String teamLegacyUidString = mvcConversionService.convert
        (
            new TeamLegacyUid
            (
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Region.EU,
                "1.200.1"
            ),
            String.class
        );
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found
            = objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "summary",
                    mvcConversionService.convert(TeamHistoryDAO.SummaryColumn.GAMES, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(List.of(new TeamHistorySummary<>(
                new RawTeamHistoryStaticData(Map.of(
                    TeamHistoryDAO.StaticColumn.LEGACY_UID,
                    teamLegacyUidString
                )),
                //1 + 0 + 3 + 3 + 6
                new RawTeamHistorySummaryData(Map.of(TeamHistoryDAO.SummaryColumn.GAMES, 15))
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
        team1.setPrimaryDataUpdated(team1.getPrimaryDataUpdated().plusSeconds(1));
        teamDAO.merge(Set.of(team1));
        teamStateDAO.takeSnapshot(List.of(1L), SeasonGenerator.DEFAULT_SEASON_START.plusHours(2));
        teamHistoryDAO.trySync();

        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found
            = objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        team1.getLegacyUid(),
                        String.class
                    )
                )
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
        assertEquals(0.5d, found.get(0).summary().data().get(TeamHistoryDAO.SummaryColumn.RATING_AVG));
    }

    private List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> getTeamHistoryTimestamps
    (
        Set<String> legacyUidStrings
    )
    throws Exception
    {
         return objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                .queryParam("teamLegacyUid", legacyUidStrings.toArray(String[]::new))
                .queryParam
                (
                    "history",
                    mvcConversionService.convert(HistoryColumn.TIMESTAMP, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
    }

    @Test
    public void testSyncBatching()
    {
        teamHistoryDAO.setSyncBatchSize(2);
        try
        {
            doTestSyncBatching();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            teamHistoryDAO.setSyncBatchSize(TeamHistoryDAO.DEFAULT_SYNC_BATCH_SIZE);
        }
    }

    private void doTestSyncBatching()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason(2);
        jdbcTemplate.update("DELETE FROM team_state");
        Team team1 = teamDAO.findById(1L).orElseThrow();
        Team team2 = teamDAO.findById(2L).orElseThrow();
        String teamLegacyUidString1 = mvcConversionService
            .convert(team1.getLegacyUid(), String.class);
        String teamLegacyUidString2 = mvcConversionService
            .convert(team2.getLegacyUid(), String.class);
        Set<String> legacyUids = Set.of(teamLegacyUidString1, teamLegacyUidString2);
        /*
            Clickhouse timestamp precision is seconds, postgresql timestamp precision is
            microseconds. The sync should be able to handle timestamp cursor with postgresql
            precision.

            In the following timestamp list of 3 values, the 2 last timestamps are equal. After
            the first batch is processed(1st and 2nd timestamps), the cursor should be smart enough
            to include the second timestamp.
         */
        teamStateDAO.takeSnapshot
        (
            List.of(team1.getId()),
            SeasonGenerator.DEFAULT_SEASON_START.plusHours(1)
        );
        teamStateDAO.takeSnapshot
        (
            List.of(team1.getId(), team2.getId()),
            SeasonGenerator.DEFAULT_SEASON_START.plusHours(2)
        );

        assertEquals(3, teamHistoryDAO.trySync());
        assertEquals(3L, clickHouseUtil.getCount(TeamHistoryDAO.TABLE_NAME));

        Comparator<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> comparator
            = Comparator.comparing(h->(String) h.staticData().data()
                .get(TeamHistoryDAO.StaticColumn.LEGACY_UID));
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> batch1 =
            getTeamHistoryTimestamps(legacyUids);
        batch1.sort(comparator);

        RawTeamHistoryStaticData staticData1 = new RawTeamHistoryStaticData(Map.of(
            TeamHistoryDAO.StaticColumn.LEGACY_UID,
            teamLegacyUidString1
        ));
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history2 =
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
                    TeamHistoryDAO.StaticColumn.LEGACY_UID,
                    teamLegacyUidString2
                )),
                new RawTeamHistoryHistoryData(Map.of(
                    HistoryColumn.TIMESTAMP,
                    List.of(SeasonGenerator.DEFAULT_SEASON_START.plusHours(2).toEpochSecond())
                ))
            );
        Assertions.assertThat(batch1)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    staticData1,
                    new RawTeamHistoryHistoryData(Map.of(
                        HistoryColumn.TIMESTAMP,
                        IntStream.rangeClosed(1, 2)
                            .mapToObj(SeasonGenerator.DEFAULT_SEASON_START::plusHours)
                            .map(OffsetDateTime::toEpochSecond)
                            .toList()
                    ))
                ),
                history2
            ));


        /*
            If there was an exception thrown mid big clickhouse insert, part of the batch may be
            persisted anyway. In this case the var cursor won't be updated, and it will point to
            an old position. If this old cursor is used in the next sync, it will insert already
            inserted rows.

            This should not happen. In such cases the actual clickhouse cursor should be used
            instead. This cursor is not precise enough(seconds vs microseconds) and should skip
            rows within the same timestamp second to avoid duplicates. Some rows may be lost, but
            it should be *extremely* rare, close to none in real world. This is an expected tradeoff
            for precision drop in clickhouse column.
         */
        //this one should be skipped because it's within already inserted timestamp second
        teamStateDAO.takeSnapshot
        (
            List.of(team1.getId()),
            SeasonGenerator.DEFAULT_SEASON_START.plusHours(2).plus(999, ChronoUnit.MILLIS)
        );
        teamStateDAO.takeSnapshot
        (
            List.of(team1.getId()),
            SeasonGenerator.DEFAULT_SEASON_START.plusHours(3)
        );
        //simulate broken cursor due to exception
        varDAO.merge
        (
            TeamHistoryDAO.SYNC_FROM_VAR_NAME,
            SeasonGenerator.DEFAULT_SEASON_START.toString()
        );
        //only 1 row starting from new timestamp second was inserted
        assertEquals(1L, teamHistoryDAO.trySync());
        assertEquals(4L, clickHouseUtil.getCount(TeamHistoryDAO.TABLE_NAME));
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> batch2 =
            getTeamHistoryTimestamps(legacyUids);
        batch2.sort(comparator);
        Assertions.assertThat(batch2)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    staticData1,
                    new RawTeamHistoryHistoryData(Map.of(
                        HistoryColumn.TIMESTAMP,
                        IntStream.rangeClosed(1, 3)
                            .mapToObj(SeasonGenerator.DEFAULT_SEASON_START::plusHours)
                            .map(OffsetDateTime::toEpochSecond)
                            .toList()
                    ))
                ),
                history2
            ));

        //just a normal 3 batch(2-2-1) update to finish the test
        IntStream.rangeClosed(4, 8)
            .forEach(i->teamStateDAO.takeSnapshot
            (
                List.of(team1.getId()),
                SeasonGenerator.DEFAULT_SEASON_START.plusHours(i)
            ));
        assertEquals(5, teamHistoryDAO.trySync());
        assertEquals(9, clickHouseUtil.getCount(TeamHistoryDAO.TABLE_NAME));
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> batch3 =
            getTeamHistoryTimestamps(legacyUids);
        batch3.sort(comparator);
        Assertions.assertThat(batch3)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    staticData1,
                    new RawTeamHistoryHistoryData(Map.of(
                        HistoryColumn.TIMESTAMP,
                        IntStream.rangeClosed(1, 8)
                            .mapToObj(SeasonGenerator.DEFAULT_SEASON_START::plusHours)
                            .map(OffsetDateTime::toEpochSecond)
                            .toList()
                    ))
                ),
                history2
            ));
    }

}
