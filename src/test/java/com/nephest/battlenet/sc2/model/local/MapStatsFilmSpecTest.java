// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class MapStatsFilmSpecTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new MapStatsFilmSpec(1, Race.TERRAN, Race.PROTOSS, Duration.ofSeconds(4)),
            new MapStatsFilmSpec(2, Race.TERRAN, Race.PROTOSS, Duration.ofSeconds(4)),

            new MapStatsFilmSpec(1, Race.PROTOSS, Race.PROTOSS, Duration.ofSeconds(4)),
            new MapStatsFilmSpec(1, Race.TERRAN, Race.TERRAN, Duration.ofSeconds(4)),
            new MapStatsFilmSpec(1, Race.TERRAN, Race.PROTOSS, Duration.ofSeconds(5))
        );
    }

}
