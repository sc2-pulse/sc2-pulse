// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PatchTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new Patch(1L, "1.1.1", true),
            new Patch(1L, "2.2.2", false),
            new Patch(2L, "1.1.1", true)
        );
    }

}
