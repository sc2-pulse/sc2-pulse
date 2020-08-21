// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.FIRST;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.SECOND;
import static org.mockito.Mockito.mock;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        League equalLeague = mock(League.class);
        Team team = new Team
        (0L, 0, Region.EU, equalLeague, FIRST, 0L, BigInteger.valueOf(0), 0L, 0, 0, 0, 0
        );
        Team equalTeam = new Team
        (1L, 0, Region.EU, mock(League.class), SECOND, 1L, BigInteger.valueOf(0), 1L, 1, 1, 1, 1
        );
        equalTeam.setGlobalRank(-1);
        equalTeam.setRegionRank(-1);
        equalTeam.setLeagueRank(-1);

        Team[] notEqualTeams = new Team[]
        {
            new Team
            (0L, 1, Region.US, equalLeague, FIRST, 0L, BigInteger.valueOf(1), 0L, 0, 0, 0, 0
            )
        };

        TestUtil.testUniqueness(team, equalTeam, notEqualTeams);
    }

}
