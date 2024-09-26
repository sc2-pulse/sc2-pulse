// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PlayerCharacterStatsTest
{

    @Test
    public void testEquality()
    {
        PlayerCharacterStats stats = new PlayerCharacterStats
        (
            null,
            0L,
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Race.PROTOSS,
            0,
            BaseLeague.LeagueType.GOLD,
            0
        );
        PlayerCharacterStats equalStats = new PlayerCharacterStats
        (
            0L,
            0L,
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Race.PROTOSS,
            1,
            BaseLeague.LeagueType.BRONZE,
            1
        );
        PlayerCharacterStats[] notEqualsStats = new PlayerCharacterStats[]
        {
            new PlayerCharacterStats
            (
                null,
                1L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Race.PROTOSS,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_2V2,
                TeamType.ARRANGED,
                Race.PROTOSS,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_2V2,
                TeamType.ARRANGED,
                Race.PROTOSS,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_1V1,
                TeamType.RANDOM,
                Race.PROTOSS,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Race.TERRAN,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                null,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            )
        };
        TestUtil.testUniqueness(stats, equalStats, (Object[]) notEqualsStats);
    }

    @Test
    public void testComparison()
    {
        PlayerCharacterStats stats1 = new PlayerCharacterStats
        (
            null,
            0L,
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Race.TERRAN,
            0,
            BaseLeague.LeagueType.GOLD,
            0
        );
        PlayerCharacterStats[] stats = new PlayerCharacterStats[]
        {
            new PlayerCharacterStats
            (
                null,
                0L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Race.TERRAN,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                1L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Race.TERRAN,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                1L,
                QueueType.LOTV_2V2,
                TeamType.ARRANGED,
                Race.TERRAN,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                1L,
                QueueType.LOTV_2V2,
                TeamType.RANDOM,
                Race.TERRAN,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            ),
            new PlayerCharacterStats
            (
                null,
                1L,
                QueueType.LOTV_2V2,
                TeamType.RANDOM,
                Race.PROTOSS,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            )
        };
        for(int i = 0; i < stats.length - 1; i++)
            assertEquals(-1, stats[i].compareTo(stats[i + 1]));
        for(int i = stats.length - 1; i > 0; i--)
            assertEquals(1, stats[i].compareTo(stats[i - 1]));
        assertEquals(0, stats1.compareTo(stats[0]));
    }

}
