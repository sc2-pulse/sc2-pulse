// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ProTeamMemberTest
{

    @Test
    public void testUniqueness()
    {
        ProTeamMember proTeamMember = new ProTeamMember(1L, 1L);
        ProTeamMember equalTeamMember = new ProTeamMember(2L, 1L);
        ProTeamMember[] notEqualTeamMembers = new ProTeamMember[]
        {
            new ProTeamMember(1L, 2L)
        };

        TestUtil.testUniqueness(proTeamMember, equalTeamMember, notEqualTeamMembers);
    }

}
