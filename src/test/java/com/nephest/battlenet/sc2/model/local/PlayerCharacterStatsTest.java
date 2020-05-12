// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

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
                0L,
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
                null,
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
                0L,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                null,
                0,
                BaseLeague.LeagueType.GOLD,
                0
            )
        };
        TestUtil.testUniqueness(stats, equalStats, notEqualsStats);
    }

}
