// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PlayerCharacterNaturalIdTest
{

    @Test
    public void testStaticCreatorEquality()
    {
        Region region = Region.EU;
        Integer realm = 1;
        Long id = 2L;
        TestUtil.testUniqueness
        (
            PlayerCharacterNaturalId.of(region, realm, id),
            PlayerCharacterNaturalId.of(region, realm, id)
        );
    }

}
