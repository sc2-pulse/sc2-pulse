// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class MapStatsFilmTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new MapStatsFilm(1, 2, 3, 4),
            new MapStatsFilm(2, 2, 3, 4),

            new MapStatsFilm(1, 20, 3, 4),
            new MapStatsFilm(1, 2, 30, 4),
            new MapStatsFilm(1, 2, 3, 40)
        );
    }

}
