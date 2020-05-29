// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class TeamMemberTest
{

    @Test
    public void testUniqueness()
    {
        TeamMember member = new TeamMember(0L, 0L, 0, 0 ,0 ,0);
        TeamMember equalTeamMember = new TeamMember(0L, 0L, 1, 1, 1 ,1);

        TeamMember[] notEqualTeamMembers = new TeamMember[]
        {
            new TeamMember(0L, 1L, 0, 0, 0, 0),
            new TeamMember(1L, 0L, 0, 0, 0, 0)
        };

        TestUtil.testUniqueness(member, equalTeamMember, notEqualTeamMembers);
    }

}

