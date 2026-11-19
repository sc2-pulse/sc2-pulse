// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PulseSeason;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.jdbc.JdbcTestUtils;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class PulseSeasonIT
{

    public static final OffsetDateTime START =
        SC2Pulse.offsetDateTime(2020, 1, 1);

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private PulseSeasonDAO pulseSeasonDAO;

    @Autowired
    private JdbcTemplate template;

    private static Season season
    (
        int battlenetId,
        Integer pulseSeasonId,
        Region region,
        int year,
        int number
    )
    {
        return new Season
        (
            null,
            battlenetId,
            pulseSeasonId,
            region,
            year,
            number,
            START,
            START.plusMonths(1)
        );
    }

    private Season merge(int battlenetId, Region region, int year, int number)
    {
        return seasonDAO.merge(season(battlenetId, null, region, year, number));
    }

    @Test
    public void whenRegionBattlenetIdDiffers_thenReuseExistingPulseSeasonId()
    {
        Season eu60 = merge(60, Region.EU, 2024, 1);
        Season eu69 = merge(69, Region.EU, 2026, 4);
        Season cn60 = merge(60, Region.CN, 2026, 4);

        assertEquals(60, eu60.getPulseSeasonId());
        assertEquals(69, eu69.getPulseSeasonId());
        assertEquals(69, cn60.getPulseSeasonId());
        assertEquals(60, cn60.getBattlenetId());

        assertEquals
        (
            69,
            seasonDAO.find(Region.CN, 60).orElseThrow().getPulseSeasonId()
        );
        assertEquals
        (
            60,
            seasonDAO.findByPulseSeasonId(Region.CN, 69)
                .orElseThrow()
                .getBattlenetId()
        );
        assertTrue(seasonDAO.findByPulseSeasonId(Region.CN, 60).isEmpty());

        assertEquals(69, seasonDAO.getMaxPulseSeasonId());
        assertEquals(69, seasonDAO.getMaxPulseSeasonId(Region.CN));
        assertEquals(60, seasonDAO.getMaxBattlenetId(Region.CN));
        Assertions.assertThat(seasonDAO.findListByPulseSeasonId(69))
            .usingRecursiveComparison()
            .withEqualsForType
            (
                OffsetDateTime::isEqual,
                OffsetDateTime.class
            )
            .isEqualTo(List.of(cn60, eu69));
        Assertions.assertThat(seasonDAO.findListByPulseSeasonId(60))
            .usingRecursiveComparison()
            .withEqualsForType
            (
                OffsetDateTime::isEqual,
                OffsetDateTime.class
            )
            .isEqualTo(List.of(eu60));
        Assertions.assertThat(seasonDAO.getLastInAllRegions())
            .containsExactly(69);
    }

    @Test
    public void whenNewSeasonIsInOffsetRegionFirst_thenCreateNextPulseSeason()
    {
        assertEquals(69, merge(69, Region.EU, 2026, 4).getPulseSeasonId());
        assertEquals(69, merge(60, Region.CN, 2026, 4).getPulseSeasonId());

        assertEquals(70, merge(61, Region.CN, 2027, 1).getPulseSeasonId());
        assertEquals(70, merge(70, Region.EU, 2027, 1).getPulseSeasonId());
        assertEquals(70, pulseSeasonDAO.findId(2027, 1).orElseThrow());
    }

    @Test
    public void whenNewestSeasonHintIsGreater_thenUseHint()
    {
        assertEquals(40, merge(40, Region.EU, 2020, 1).getPulseSeasonId());
        assertEquals(45, merge(45, Region.EU, 2020, 2).getPulseSeasonId());
    }

    @Test
    public void whenOlderSeasonFitsBetweenNeighbors_thenUseHint()
    {
        assertEquals(40, merge(40, Region.EU, 2020, 1).getPulseSeasonId());
        assertEquals(42, merge(42, Region.EU, 2020, 3).getPulseSeasonId());
        assertEquals(39, merge(39, Region.EU, 2019, 1).getPulseSeasonId());
        assertEquals(41, merge(41, Region.EU, 2020, 2).getPulseSeasonId());
    }

    @Test
    public void whenOlderSeasonDoesNotFitBetweenNeighbors_thenThrow()
    {
        merge(40, Region.EU, 2020, 1);
        merge(41, Region.EU, 2020, 2);

        assertThrows
        (
            InvalidDataAccessApiUsageException.class,
            ()->merge(45, Region.EU, 2019, 1)
        );
        assertTrue(seasonDAO.find(Region.EU, 45).isEmpty());
        assertTrue(pulseSeasonDAO.findId(2019, 1).isEmpty());
    }

    @Test
    public void whenExistingSeasonYearAndNumberChange_thenThrow()
    {
        assertEquals(40, merge(40, Region.EU, 2020, 1).getPulseSeasonId());

        assertThrows
        (
            DataIntegrityViolationException.class,
            ()->merge(40, Region.EU, 2020, 2)
        );
        Season found = seasonDAO.find(Region.EU, 40).orElseThrow();
        assertEquals(40, found.getPulseSeasonId());
        assertEquals(2020, found.getYear());
        assertEquals(1, found.getNumber());
        //the new pulse season is rolled back
        assertTrue(pulseSeasonDAO.findId(2020, 2).isEmpty());
    }

    @Test
    public void whenPulseSeasonIdIsSet_thenIgnoreIt()
    {
        merge(69, Region.EU, 2026, 4);

        Season created = seasonDAO.create(season(60, 5, Region.CN, 2026, 4));
        assertEquals(69, created.getPulseSeasonId());
        Season merged = seasonDAO.merge(season(60, 5, Region.KR, 2026, 4));
        assertEquals(69, merged.getPulseSeasonId());

        assertEquals
        (
            Set.of(69),
            seasonDAO.findListByPulseSeasonId(null).stream()
                .map(Season::getPulseSeasonId)
                .collect(Collectors.toSet())
        );
        assertTrue(pulseSeasonDAO.findId(2026, 4).isPresent());
        assertEquals
        (
            1,
            JdbcTestUtils.countRowsInTable(template, "pulse_season")
        );
    }

    @Test
    public void whenConcurrentTransactionCreatesSamePulseSeason_thenReuseIt()
    throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try(Connection connection = template.getDataSource().getConnection())
        {
            connection.setAutoCommit(false);
            insertPulseSeason(connection, 69, 2026, 4);

            //different hint to verify that the committed id is used
            Future<PulseSeason> merged = executor.submit(()->pulseSeasonDAO
                .merge(2026, 4, 70));
            DbTestUtil.waitForLock(connection);
            connection.commit();

            Assertions
                .assertThat(merged.get
                (
                    DbTestUtil.LOCK_WAIT_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS
                ))
                .usingRecursiveComparison()
                .isEqualTo(new PulseSeason(69, 2026, 4));
            assertEquals
            (
                1,
                JdbcTestUtils.countRowsInTable(template, "pulse_season")
            );
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void whenConcurrentTransactionTakesCandidateId_thenThrow()
    throws Exception
    {
        pulseSeasonDAO.merge(2026, 3, 68);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try(Connection connection = template.getDataSource().getConnection())
        {
            connection.setAutoCommit(false);
            insertPulseSeason(connection, 69, 2026, 4);

            Future<PulseSeason> merged = executor.submit(()->pulseSeasonDAO
                .merge(2026, 5, 69));
            DbTestUtil.waitForLock(connection);
            connection.commit();

            ExecutionException exception = assertThrows
            (
                ExecutionException.class,
                ()->merged.get
                (
                    DbTestUtil.LOCK_WAIT_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS
                )
            );
            assertInstanceOf
            (
                InvalidDataAccessApiUsageException.class,
                exception.getCause()
            );
            assertTrue(pulseSeasonDAO.findId(2026, 5).isEmpty());
        }
        finally
        {
            executor.shutdownNow();
        }

        //retry
        assertEquals
        (
            70,
            pulseSeasonDAO.merge(2026, 5, 69).getId()
        );
    }

    private static void insertPulseSeason
    (
        Connection connection,
        int id,
        int year,
        int number
    )
    throws SQLException
    {
        try(PreparedStatement statement = connection.prepareStatement
        (
            "INSERT INTO pulse_season(id, year, number) VALUES(?, ?, ?)"
        ))
        {
            statement.setInt(1, id);
            statement.setInt(2, year);
            statement.setInt(3, number);
            statement.executeUpdate();
        }
    }

    @Test
    public void whenSharedBattlenetIds_thenPulseSeasonIdEqualsBattlenetId()
    {
        for(Region region : Set.of(Region.EU, Region.US, Region.CN))
        {
            merge(1, region, 2020, 1);
            merge(2, region, 2020, 2);
        }

        List<Season> seasons = seasonDAO.findListByPulseSeasonId(null);
        assertEquals(6, seasons.size());
        assertTrue(seasons.stream()
            .allMatch(s->s.getPulseSeasonId().equals(s.getBattlenetId())));
        assertEquals
        (
            Set.of(1, 2),
            seasons.stream()
                .map(Season::getPulseSeasonId)
                .collect(Collectors.toSet())
        );
    }

}
