// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import static com.nephest.battlenet.sc2.model.Region.EU;
import static com.nephest.battlenet.sc2.model.Region.US;

public class SeasonTest
{

    @Test
    public void testUniqueness()
    {
        Season season = new Season(0l, 0l, EU, 0, 0);
        Season equalSeason = new Season(1l, 0l, EU, 2, 3);
        Season[] notEqualSeasons = new Season[]
        {
            new Season(0l, 0l, US, 0, 0),
            new Season(0l, 1l, EU, 0, 0)
        };

        TestUtil.testUniqueness(season, equalSeason, notEqualSeasons);
    }

}
