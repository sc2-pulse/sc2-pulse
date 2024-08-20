// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import reactor.core.Disposable;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderUpdateIT
{

    @Autowired
    private EventService eventService;

    @Autowired
    private LadderUpdateDAO ladderUpdateDAO;

    @Autowired
    private UpdateService updateService;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        updateService.setPreviousLadderUpdateOffsetDateTime(null);
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
    public void whenLadderUpdateEventIsTriggered_thenItShouldBePersisted()
    throws InterruptedException
    {
        BlockingQueue<LadderUpdate> evts = new ArrayBlockingQueue<>(5);
        Disposable sub = updateService.getSaveLadderUpdateEvent().subscribe(evts::add);
        
        try
        {
            eventService.createLadderUpdateEvent(LadderUpdateData.EMPTY);
            OffsetDateTime before = SC2Pulse.offsetDateTime();
            eventService.createLadderUpdateEvent(new LadderUpdateData(false, List.of(),
                List.of
                (
                    Map.of
                    (
                        Region.EU,
                        new LadderUpdateTaskContext<>
                        (
                            new Season(),
                            Map.of(QueueType.LOTV_1V1, Set.of(BaseLeague.LeagueType.BRONZE)),
                            List.of()
                        )
                    ),
                    Map.of
                    (
                        Region.US, new LadderUpdateTaskContext<>
                        (
                            new Season(),
                            Map.of
                            (
                                QueueType.LOTV_1V1,
                                Set.of(BaseLeague.LeagueType.BRONZE, BaseLeague.LeagueType.SILVER),

                                QueueType.LOTV_2V2,
                                Set.of(BaseLeague.LeagueType.BRONZE)
                            ),
                            List.of()
                        )
                    ),
                    Map.of
                    (
                        Region.US, new LadderUpdateTaskContext<>
                        (
                            new Season(),
                            Map.of
                            (
                                QueueType.LOTV_1V1,
                                Set.of(BaseLeague.LeagueType.GOLD)
                            ),
                            List.of()
                        )
                    )
                )
            ));
            for(int i = 0; i < 5; i++) evts.poll(5, TimeUnit.SECONDS);
            OffsetDateTime after = SC2Pulse.offsetDateTime();

            List<LadderUpdate> updates = ladderUpdateDAO.getAll();
            updates.sort(Comparator.comparing(LadderUpdate::getRegion)
                .thenComparing(LadderUpdate::getQueueType)
                .thenComparing(LadderUpdate::getLeagueType));
            Assertions.assertThat(updates).usingRecursiveComparison().ignoringFields(
                "created", "duration").isEqualTo(List.of(
                new LadderUpdate(Region.US, QueueType.LOTV_1V1, BaseLeague.LeagueType.BRONZE,
                    null,  null
                ),
                new LadderUpdate(Region.US, QueueType.LOTV_1V1, BaseLeague.LeagueType.SILVER,
                    null, null
                ),
                new LadderUpdate(Region.US, QueueType.LOTV_1V1, BaseLeague.LeagueType.GOLD,
                    null, null
                ),
                new LadderUpdate(Region.US, QueueType.LOTV_2V2, BaseLeague.LeagueType.BRONZE,
                    null, null
                ),
                new LadderUpdate(Region.EU, QueueType.LOTV_1V1, BaseLeague.LeagueType.BRONZE,
                    null, null
                )
            ));
            updates.forEach(u ->
            {
                assertFalse(u.getCreated().isBefore(before));
                assertFalse(u.getCreated().isAfter(after));
                assertTrue(u.getDuration().toSeconds() >= 0);
                assertTrue(u.getDuration().compareTo(Duration.between(before, after)) <= 0);
            });
        }
        finally
        {
            sub.dispose();
        }
    }

}
