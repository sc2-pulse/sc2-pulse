// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PulseSeasonTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new PulseSeason(1, 2020, 1),
            new PulseSeason(2, 2020, 1),

            new PulseSeason(1, 2021, 1),
            new PulseSeason(1, 2020, 2)
        );
    }

}
