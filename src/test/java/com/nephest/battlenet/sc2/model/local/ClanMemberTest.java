// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ClanMemberTest
{

    @Test
    public void testUniqueness()
    {
        ClanMember clan = new ClanMember(1L, 1);
        ClanMember equalClan = new ClanMember(1L, 2);
        ClanMember[] notEqualClans = new ClanMember[]
        {
            new ClanMember(2L, 1)
        };

        TestUtil.testUniqueness(clan, equalClan, (Object[]) notEqualClans);
    }

}
