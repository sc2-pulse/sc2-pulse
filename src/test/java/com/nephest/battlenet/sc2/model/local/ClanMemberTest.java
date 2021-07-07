// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

public class ClanMemberTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalDate = OffsetDateTime.now();
        ClanMember member = new ClanMember(1, 1L, equalDate);
        ClanMember equalMember = new ClanMember(0, 1L, equalDate.minusSeconds(1));
        ClanMember[] notEqualMembers = new ClanMember[]{new ClanMember(1, 0L, equalDate)};

        TestUtil.testUniqueness(member, equalMember, notEqualMembers);
    }

}
