// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class AccountTest
{

    @Test
    public void testUniqueness()
    {
        Account account = new Account(0L, Partition.GLOBAL, "Name#123");
        Account equalsAccount = new Account(1L, Partition.GLOBAL, "Name#123");

        Account[] notEqualAccounts = new Account[]
        {
            new Account(0L, Partition.CHINA, "Name#123"),
            new Account(0L, Partition.GLOBAL, "DiffName#123"),
        };

        TestUtil.testUniqueness(account, equalsAccount, (Object[]) notEqualAccounts);
    }

    @Test
    public void testHiddenAccount()
    {
        Account account = new Account(0L, Partition.GLOBAL, "tag#123", true);
        assertEquals(BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME, account.getFakeOrRealBattleTag());
        account.setHidden(false);
        assertEquals(account.getBattleTag(), account.getFakeOrRealBattleTag());
    }

}

