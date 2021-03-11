// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        Team team = new Team(0L, Region.EU, 0, 0, BigInteger.valueOf(0), 0L, 0, 0, 0, 0);
        Team equalTeam = new Team(1L, Region.EU, 1, 1, BigInteger.valueOf(0), 1L, 1, 1, 1, 1);
        equalTeam.setGlobalRank(-1);
        equalTeam.setRegionRank(-1);
        equalTeam.setLeagueRank(-1);

        Team[] notEqualTeams = new Team[]
        {
            new Team(0L, Region.US, 0, 0, BigInteger.valueOf(1), 0L, 0, 0, 0, 0)
        };

        TestUtil.testUniqueness(team, equalTeam, notEqualTeams);
    }

}
