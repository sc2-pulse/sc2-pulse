// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.util.TestDatabaseLifecycleService;
import com.nephest.battlenet.sc2.service.EventService;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class TeamStateServiceIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

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

    private final List<Disposable> disposables = new ArrayList<>(1);
    private static int mainLengthBefore;
    private static int secondaryLengthBefore;

    @BeforeEach
    public void beforeEach()
    throws Exception
    {
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
        @Autowired TestDatabaseLifecycleService lifecycleService,
        @Autowired TeamStateService teamStateService
    )
    throws Exception
    {
        try
        {
            lifecycleService.initDb();
            teamStateService.reset();
        }
        finally
        {
            lifecycleService.clearDb();
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

    public static Stream<Arguments> testRemoveExpired()
    {
        return Stream.of
        (
            Arguments.of
            (
                QueueType.LOTV_1V1,
                (Function<TeamStateService, Integer>) TeamStateService::getMainLengthDays
            ),
            Arguments.of
            (
                QueueType.LOTV_2V2,
                (Function<TeamStateService, Integer>) TeamStateService::getSecondaryLengthDays
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testRemoveExpired
    (
        QueueType queueType,
        Function<TeamStateService, Integer> depthSupplier
    )
    throws Exception
    {
        int depth = depthSupplier.apply(teamStateService);
        OffsetDateTime expiredStart = SC2Pulse.offsetDateTime().minusDays(depth).minusSeconds(1);
        OffsetDateTime recentOdt = expiredStart.plusDays(1);
        seasonGenerator.generateSeason
        (
            List.of(new Season(null, 10, Region.EU, expiredStart.getYear(), 1, expiredStart, expiredStart.plusMonths(1))),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(queueType),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        assertEquals(1, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state"));
        List<Long> teamIds = teamDAO.findIds(Region.EU, 10);
        teamStateDAO.takeSnapshot(teamIds, recentOdt);
        teamStateDAO.takeSnapshot(teamIds, OffsetDateTime.MIN);
        assertEquals(3, JdbcTestUtils.countRowsInTable(jdbcTemplate, "team_state"));

        updateService.updated(SC2Pulse.instant());
        BlockingQueue<LadderUpdateData> eventData = new ArrayBlockingQueue<>(1);
        disposables.add(teamStateService.getUpdateEvent().subscribe(eventData::add));
        eventService.createLadderUpdateEvent(createUpdateData(11));
        eventData.take();

        List<LadderTeamState> lts = ladderTeamStateDAO
            .find(Set.of(teamDAO.findById(teamIds.get(0)).orElseThrow().getLegacyUid()));
        assertEquals(1, lts.size());
        assertEquals(recentOdt, lts.get(0).getTeamState().getDateTime());
    }

    private void whenExceptionIsThrownMidProcess_thenThereShouldBeNoLeftoversInDb
    (
        Consumer<Region> stub,
        Consumer<Integer> verifier
    )
    throws InterruptedException
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime()
            .minusDays(teamStateService.getMainLengthDays())
            .plusHours(1);
        OffsetDateTime end = start.plusDays(3);
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 10, Region.EU, start.getYear(), 1, start, end),
                new Season(null, 11, Region.EU, start.getYear(), 2, end, end.plusDays(3))
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

}
