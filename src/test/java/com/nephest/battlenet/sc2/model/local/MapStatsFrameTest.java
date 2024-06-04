// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class MapStatsFrameTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new MapStatsFrame(1, 2, 3, 4),
            new MapStatsFrame(1, 2, 10, 11),

            new MapStatsFrame(10, 2, 3, 4),
            new MapStatsFrame(1, 20, 3, 4)
        );
    }

    @Test
    public void testCompareTo()
    {
        MapStatsFrame[] ordered = new MapStatsFrame[]
        {
            new MapStatsFrame(1, 2, 0, 0),
            new MapStatsFrame(1, 3, 0, 0),
            new MapStatsFrame(1, null, 0, 0),
            new MapStatsFrame(2, 1, 0, 0)
        };
        MapStatsFrame[] unordered = new MapStatsFrame[]
        {
            ordered[1],
            ordered[3],
            ordered[2],
            ordered[0]
        };
        Arrays.sort(unordered);
        assertArrayEquals(ordered, unordered);
    }

}
