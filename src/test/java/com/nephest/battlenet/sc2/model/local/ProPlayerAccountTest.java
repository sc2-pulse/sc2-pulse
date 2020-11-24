// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ProPlayerAccountTest
{

    @Test
    public void testUniqueness()
    {
        ProPlayerAccount proPlayerAccount = new ProPlayerAccount(1L, 1L);
        ProPlayerAccount equalProPlayerAccount = new ProPlayerAccount(1L, 1L);
        ProPlayerAccount[] notEqualProAccounts = new ProPlayerAccount[]
        {
            new ProPlayerAccount(1L, 2L),
            new ProPlayerAccount(2L, 1L)
        };
        TestUtil.testUniqueness(proPlayerAccount, equalProPlayerAccount, notEqualProAccounts);
    }

}
