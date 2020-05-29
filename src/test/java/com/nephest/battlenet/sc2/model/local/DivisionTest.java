// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class DivisionTest
{

    @Test
    public void testUniqueness()
    {
        Division division = new Division(0L, 0L, 0L);
        Division equalDivision = new Division(1L, 0L, 0L);

        Division[] notEqualDivisions = new Division[]
        {
            new Division(0L, 1L, 0L),
            new Division(0L, 0L, 1L)
        };

        TestUtil.testUniqueness(division, equalDivision, notEqualDivisions);
    }

}

