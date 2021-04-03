// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        Team team = new Team(0L, Region.EU, QueueType.LOTV_1V1, 0, 0, BigInteger.valueOf(0), 0L, 0, 0, 0, 0);
        Team equalTeam = new Team(1L, Region.EU, QueueType.LOTV_2V2, 1, 1, BigInteger.valueOf(0), 1L, 1, 1, 1, 1);
        equalTeam.setGlobalRank(-1);
        equalTeam.setRegionRank(-1);
        equalTeam.setLeagueRank(-1);

        Team[] notEqualTeams = new Team[]
        {
            new Team(0L, Region.US, QueueType.LOTV_1V1, 0, 0, BigInteger.valueOf(1), 0L, 0, 0, 0, 0)
        };

        TestUtil.testUniqueness(team, equalTeam, notEqualTeams);
    }

    @Test
    public void testShouldUpdate()
    {
        Team team = new Team(0L, Region.EU, QueueType.LOTV_1V1, 0, 0, BigInteger.valueOf(0), 0L, 0, 0, 0, 0);
        Team equalTeam = new Team(1L, Region.US, QueueType.LOTV_2V2, 0, 1, BigInteger.valueOf(1), 1L, 0, 0, 0, 1);
        Team notEqualTeam1 = new Team(0L, Region.EU, QueueType.LOTV_1V1, 1, 0, BigInteger.valueOf(0), 0L, 0, 0, 0, 0);
        Team notEqualTeam2 = new Team(0L, Region.EU, QueueType.LOTV_1V1, 0, 0, BigInteger.valueOf(0), 0L, 1, 1, 1, 0);

        assertFalse(Team.shouldUpdate(team, equalTeam));
        assertTrue(Team.shouldUpdate(team, notEqualTeam1));
        assertTrue(Team.shouldUpdate(team, notEqualTeam2));
    }

}
