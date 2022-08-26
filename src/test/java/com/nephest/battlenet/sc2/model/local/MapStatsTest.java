// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class MapStatsTest
{

    @Test
    public void testUniqueness()
    {
        MapStats stats = new MapStats(null, 0, 0, Race.TERRAN, Race.PROTOSS, 0, 0, 0, 0, 0, 0);
        MapStats equalStats = new MapStats(1, 0, 0, Race.TERRAN, Race.PROTOSS, 1, 1, 1, 1, 1, 1);
        MapStats[] notEqualStats = new MapStats[]
        {
            new MapStats(null, 1, 0, Race.TERRAN, Race.PROTOSS, 0, 0, 0, 0, 0, 0),
            new MapStats(null, 0, 1, Race.TERRAN, Race.PROTOSS, 0, 0, 0, 0, 0, 0),
            new MapStats(null, 0, 0, Race.ZERG, Race.PROTOSS, 0, 0, 0, 0, 0, 0),
            new MapStats(null, 0, 0, Race.TERRAN, Race.ZERG, 0, 0, 0, 0, 0, 0),
        };
        TestUtil.testUniqueness(stats, equalStats, (Object[]) notEqualStats);
    }

}
