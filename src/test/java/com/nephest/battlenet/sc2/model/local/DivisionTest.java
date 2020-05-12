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
        Division division = new Division(0l, 0l, 0l);
        Division equalDivision = new Division(1l, 0l, 0l);

        Division[] notEqualDivisions = new Division[]
        {
            new Division(0l, 1l, 0l),
            new Division(0l, 0l, 1l)
        };

        TestUtil.testUniqueness(division, equalDivision, notEqualDivisions);
    }

}

