// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PlayerCharacterNaturalIdImplTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new PlayerCharacterNaturalIdImpl(Region.EU, 1, 2L),
            new PlayerCharacterNaturalIdImpl(Region.EU, 1, 2L),

            new PlayerCharacterNaturalIdImpl(Region.US, 1, 2L),
            new PlayerCharacterNaturalIdImpl(Region.EU, 2, 2L),
            new PlayerCharacterNaturalIdImpl(Region.EU, 1, 1L)
        );
    }

}
