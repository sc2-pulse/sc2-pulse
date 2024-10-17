// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.Region.EU;
import static com.nephest.battlenet.sc2.model.Region.US;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class SeasonTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalDate = SC2Pulse.offsetDateTime(2020, 1, 1);
        Season season = new Season(0, 0, EU, 0, 0, equalDate, equalDate);
        Season equalSeason = new Season(1, 0, EU, 2, 3, equalDate.plusDays(1), equalDate.plusDays(1));
        Season[] notEqualSeasons = new Season[]
        {
            new Season(0, 0, US, 0, 0, equalDate, equalDate),
            new Season(0, 1, EU, 0, 0, equalDate, equalDate)
        };

        TestUtil.testUniqueness(season, equalSeason, (Object[]) notEqualSeasons);
    }

}
