// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class QueueStatsTest
{

    @Test
    public void testUniqueness()
    {
        QueueStats stats = new QueueStats(0L, 0L, QueueType.LOTV_1V1, TeamType.ARRANGED, 0L, 0);
        QueueStats equalStats = new QueueStats(1L, 0L, QueueType.LOTV_1V1, TeamType.ARRANGED, 1L, 1);

        QueueStats[] notEqualStats = new QueueStats[]
        {
            new QueueStats(0L, 1L, QueueType.LOTV_2V2, TeamType.RANDOM, 0L, 0),
            new QueueStats(0L, 0L, QueueType.LOTV_2V2, TeamType.RANDOM, 0L, 0),
            new QueueStats(0L, 0L, QueueType.LOTV_1V1, TeamType.RANDOM, 0L, 0)
        };

        TestUtil.testUniqueness(stats, equalStats, notEqualStats);
    }

}
