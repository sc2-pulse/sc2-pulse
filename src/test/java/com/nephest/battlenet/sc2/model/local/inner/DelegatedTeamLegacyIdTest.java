// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Team;
import org.junit.jupiter.api.Test;

public class DelegatedTeamLegacyIdTest
{

    @Test
    public void testDelegatedProperties()
    {
        Team team = new Team();
        DelegatedTeamLegacyUid uid = new DelegatedTeamLegacyUid(team);

        //initial values
        assertEquals(team, uid.getTeam());
        assertNull(uid.getQueueType());
        assertNull(uid.getTeamType());
        assertNull(uid.getRegion());
        assertNull(uid.getId());

        //team set, uid get
        TeamLegacyId legacyId1 = TeamLegacyId.trusted("1");
        team.setQueueType(QueueType.LOTV_1V1);
        team.setTeamType(TeamType.ARRANGED);
        team.setRegion(Region.US);
        team.setLegacyId(legacyId1);

        assertEquals(QueueType.LOTV_1V1, uid.getQueueType());
        assertEquals(TeamType.ARRANGED, uid.getTeamType());
        assertEquals(Region.US, uid.getRegion());
        assertEquals(legacyId1, uid.getId());

        //uid set, team get
        TeamLegacyId legacyId2 = TeamLegacyId.trusted("2");
        uid.setQueueType(QueueType.LOTV_2V2);
        uid.setTeamType(TeamType.RANDOM);
        uid.setRegion(Region.EU);
        uid.setId(legacyId2);

        assertEquals(QueueType.LOTV_2V2, team.getQueueType());
        assertEquals(TeamType.RANDOM, team.getTeamType());
        assertEquals(Region.EU, team.getRegion());
        assertEquals(legacyId2, team.getLegacyId());
    }

}
