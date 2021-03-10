// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class LeagueStatsTest
{

    @Test
    public void testUniqueness()
    {
        LeagueStats stats = new LeagueStats(0, 0, 0, 0, 0, 0);
        LeagueStats equalStats = new LeagueStats(0, 1, 1, 1, 1, 1);

        LeagueStats[] notEqualStats = new LeagueStats[]
        {
            new LeagueStats(1, 0, 0, 0, 0, 0)
        };

        TestUtil.testUniqueness(stats, equalStats, notEqualStats);
    }

}
