// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateArchiveDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryHistoryData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class TeamStateServiceIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private TeamStateArchiveDAO teamStateArchiveDAO;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private TeamStateService teamStateService;

    @Autowired
    private EventService eventService;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired @Qualifier("minimalConversionService")
    private ConversionService minConversionService;

    private final List<Disposable> disposables = new ArrayList<>(1);
    private static int mainLengthBefore;
    private static int secondaryLengthBefore;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        teamStateService.reset();
        updateService.updated(Instant.MIN);
        mainLengthBefore = teamStateService.getMainLengthDays();
        secondaryLengthBefore = teamStateService.getSecondaryLengthDays();
        teamStateService.setMainLengthDays(360);
        teamStateService.setSecondaryLengthDays(180);
    }

    @AfterEach
    public void afterEach()
    {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
        teamStateService.setMainLengthDays(mainLengthBefore);
        teamStateService.setSecondaryLengthDays(secondaryLengthBefore);
    }

    @AfterAll
    public static void afterAll
    (
        @Autowired DataSource dataSource,
        @Autowired TeamStateService teamStateService
    )
    throws SQLException
    {
        teamStateService.reset();
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    private static LadderUpdateData createUpdateData(int season)
    {
        return new LadderUpdateData
        (
            false,
            List.of(),
            List.of(Map.of(
                Region.EU,
                new LadderUpdateTaskContext<>
                (
                    new Season
                    (
                        null,
                        season,
                        Region.EU,
                        2020,
                        1,
                        SC2Pulse.offsetDateTime(),
                        SC2Pulse.offsetDateTime()
                    ),
                    Map.of(),
                    List.of()
                )
            ))
        );
    }

    private void createTeamSnapshot
    (
        long id,
        QueueType queueType,
        TeamLegacyId legacyId,
        long rating,
        int wins,
        OffsetDateTime odt
    )
    {
        teamDAO.merge(Set.of(new Team(
            null,
            10,
            Region.EU,
            new BaseLeague
            (
                BaseLeague.LeagueType.BRONZE,
                queueType,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            legacyId,
            1,
            rating, wins, 1, 1, 0,
            null, odt, odt
        )));
        teamStateDAO.takeSnapshot(List.of(id), odt);
    }

    public static Stream<Arguments> testArchive()
    {
        return Stream.of
        (
            Arguments.of
            (
                QueueType.LOTV_1V1,
                (Function<TeamStateService, Integer>) TeamStateService::getMainLengthDays,
                10L,
                TeamLegacyId.trusted("1.90.4")
            ),
            Arguments.of
            (
                QueueType.LOTV_2V2,
                (Function<TeamStateService, Integer>) TeamStateService::getSecondaryLengthDays,
                10L,
                TeamLegacyId.trusted("1.90.~1.91.")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testArchive
    (
        QueueType queueType,
        Function<TeamStateService, Integer> depthSupplier,
        long teamId,
        TeamLegacyId legacyId
    )
    throws Exception
    {
        int depth = depthSupplier.apply(teamStateService);
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusDays(depth).minusYears(2);
        OffsetDateTime end = start.plusYears(1);
        int teamCount = TeamStateService.TEAM_ARCHIVE_BATCH_SIZE + 1;
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 10, Region.EU, start.getYear(), 1, start, end),
                new Season(null, 11, Region.EU, start.getYear(), 2, end, end.plusMonths(1))
            ),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(queueType),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            teamCount
        );

        OffsetDateTime odtStart = SC2Pulse.offsetDateTime().minusDays(depth);

        jdbcTemplate.update("UPDATE team SET last_played = null WHERE id = " + teamId);
        //archived min
        createTeamSnapshot(teamId, queueType, legacyId, 0L, 11, odtStart.minusSeconds(3));
        //expired and not archived, should be removed
        createTeamSnapshot(teamId, queueType, legacyId, 99L, 12, odtStart.minusSeconds(2));
        //archived max
        createTeamSnapshot(teamId, queueType, legacyId, 100L, 13, odtStart.minusSeconds(1));
        //not expired and not archived
        createTeamSnapshot(teamId, queueType, legacyId, 11L, 14, odtStart.plusMinutes(1));
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = ? ",
            odtStart.plusMinutes(1), teamId
        );

        BlockingQueue<LadderUpdateData> eventData = new ArrayBlockingQueue<>(1);
        disposables.add(teamStateService.getUpdateEvent().subscribe(eventData::add));

        //no season to archive
        eventService.createLadderUpdateEvent(createUpdateData(9));
        eventData.take();
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state_archive"));

        //no season to archive
        eventService.createLadderUpdateEvent(createUpdateData(10));
        eventData.take();
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state_archive"));

        //archive previous because it was not archived yet
        eventService.createLadderUpdateEvent(createUpdateData(11));
        eventData.take();
        assertEquals
        (
            teamCount * 2 + 1, //+1 final snapshot
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state_archive")
        );

        //no new archive because season 11 is the current season
        eventService.createLadderUpdateEvent(createUpdateData(12));
        eventData.take();
        assertEquals
        (
            teamCount * 2 + 1, //+1 final snapshot
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state_archive")
        );

        assertEquals
        (
            teamCount * 3 + 4, //+4 snapshots created earlier
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state")
        );

        teamStateService.getLastClearInstantVar().setValueAndSave(start.toInstant());
        updateService.updated(SC2Pulse.instant());
        eventService.createLadderUpdateEvent(createUpdateData(12));
        eventData.take();
        //expired team states have been removed
        assertEquals
        (
            (teamCount * 2 + 2), //+ not expired and last
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state")
        );

        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history
            = objectMapper.readValue(mvc.perform(get("/api/team/group/history")
                .queryParam("teamId", String.valueOf(teamId))
                .queryParam
                (
                    "history",
                    mvcConversionService
                        .convert(TeamHistoryDAO.HistoryColumn.TIMESTAMP, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertTrue(AssertionUtil.numberListEquals(
            Stream.of
            (
                odtStart.minusSeconds(3), //min rating
                odtStart.minusSeconds(1), //max rating
                odtStart.plusMinutes(1), //not expired yet
                //final snapshot
                odtStart.plusMinutes(1).plus(TeamStateService.FINAL_TEAM_SNAPSHOT_OFFSET)
            )
                .map(odt->minConversionService.convert(odt, Object.class))
                .toList(),
            history.get(0).history().data().get(TeamHistoryDAO.HistoryColumn.TIMESTAMP)
        ));

        //remove all expect archive
        teamStateService.setMainLengthDays(0);
        teamStateService.setSecondaryLengthDays(0);
        teamStateService.getLastClearInstantVar().setValueAndSave(start.toInstant());
        updateService.updated(SC2Pulse.instant());
        eventService.createLadderUpdateEvent(createUpdateData(12));
        eventData.take();
        assertEquals
        (
            (teamCount * 2 + 1), // + last timestamp
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state")
        );
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> history2
            = objectMapper.readValue(mvc.perform(get("/api/team/group/history")
                .queryParam("teamId", String.valueOf(teamId))
                .queryParam
                (
                    "history",
                    mvcConversionService
                        .convert(TeamHistoryDAO.HistoryColumn.TIMESTAMP, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertTrue(AssertionUtil.numberListEquals(
            Stream.of
            (
                odtStart.minusSeconds(3), //min rating
                odtStart.minusSeconds(1), //max rating
                //odtStart.plusMinutes(1), removed
                //last timestamp
                odtStart.plusMinutes(1).plus(TeamStateService.FINAL_TEAM_SNAPSHOT_OFFSET)
            )
                .map(odt->minConversionService.convert(odt, Object.class))
                .toList(),
            history2.get(0).history().data().get(TeamHistoryDAO.HistoryColumn.TIMESTAMP)
        ));
    }

    private void whenExceptionIsThrownMidProcess_thenThereShouldBeNoLeftoversInDb
    (
        Consumer<Region> stub,
        Consumer<Integer> verifier
    )
    throws InterruptedException
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);
        OffsetDateTime end = start.plusMonths(1);
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 10, Region.EU, start.getYear(), 1, start, end),
                new Season(null, 11, Region.EU, start.getYear(), 2, end, end.plusMonths(1))
            ),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            3
        );
        stub.accept(Region.EU);

        teamStateService.subToEvents(Schedulers.immediate());
        BlockingQueue<LadderUpdateData> eventData = new ArrayBlockingQueue<>(1);
        disposables.add(teamStateService.getUpdateEvent().subscribe(eventData::add));

        eventService.createLadderUpdateEvent(createUpdateData(11));

        //generate a successful event to get past the blocking queue call
        reset(varDAO);
        eventService.createLadderUpdateEvent(createUpdateData(9));
        eventData.take();
        verifier.accept(6);
    }

    @Test
    public void whenExceptionIsThrownMidArchive_thenThereShouldBeNoArchiveLeftoversInDb()
    throws InterruptedException
    {
        whenExceptionIsThrownMidProcess_thenThereShouldBeNoLeftoversInDb
        (
            region->doThrow(new RuntimeException("test"))
                .when(varDAO)
                .merge
                (
                    eq(teamStateService.getLastArchiveSeasonVars().get(region).getKey()),
                    anyString()
                ),
            teamCount->
            {
                //archive was created at some point
                verify(teamStateArchiveDAO, atLeastOnce()).archive(anySet());
                //no traces left
                assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state_archive"));
            }
        );
    }

    @Test
    public void whenExceptionIsThrownMidFinalization_thenThereShouldBeNoFinalTeamStateLeftoversInDb()
    throws InterruptedException
    {
        whenExceptionIsThrownMidProcess_thenThereShouldBeNoLeftoversInDb
        (
            region->doThrow(new RuntimeException("test"))
                .when(varDAO)
                .merge(eq(teamStateService.getLastFinalizedSeasonVars().get(region).getKey()),
                    anyString()),
            teamCount->
            {
                //season was finalized at some point
                verify(teamStateDAO, atLeastOnce()).takeSnapshot(anyList(), any());
                //no traces left except for the original 6 snapshots from season generation
                assertEquals(teamCount, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state"));
            }
        );
    }

    @Test
    public void whenFirstClear_thenClearFromMinOdt()
    throws Exception
    {
        OffsetDateTime start = OffsetDateTime.MIN;
        OffsetDateTime end = start.plusMonths(1);
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 10, Region.EU, 2020, 1, start, end)
            ),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        assertEquals(1, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state"));
        updateService.updated(SC2Pulse.instant());
        BlockingQueue<LadderUpdateData> eventData = new ArrayBlockingQueue<>(1);
        disposables.add(teamStateService.getUpdateEvent().subscribe(eventData::add));
        eventService.createLadderUpdateEvent(createUpdateData(11));
        eventData.take();
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state"));
    }


}
