// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class AccountPropertyTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new AccountProperty(1L, AccountProperty.PropertyType.PASSWORD, "val1"),
            new AccountProperty(1L, AccountProperty.PropertyType.PASSWORD, "val2"),

            new AccountProperty(2L, AccountProperty.PropertyType.PASSWORD, "val1")
        );
    }

}
