// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.FIRST;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        BaseLeague league = new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED);
        BaseLeague equalLeague = new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.RANDOM);
        BaseLeague notEqualLeague = new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED);
        Team team = new Team(0L, 0, Region.EU, league, FIRST, BigInteger.ZERO, 0,
            0L, 0, 0, 0,0);
        Team equalTeam = new Team(1L, 0, Region.EU, equalLeague, SECOND, BigInteger.ZERO, 1,
            1L, 1, 1, 1, 1);
        equalTeam.setGlobalRank(-1);
        equalTeam.setRegionRank(-1);
        equalTeam.setLeagueRank(-1);

        Team[] notEqualTeams = new Team[]
        {
            new Team(0L, 1, Region.EU, league, FIRST, BigInteger.ZERO, 0, 0L, 0, 0, 0,0),
            new Team(0L, 0, Region.US, league, FIRST, BigInteger.ZERO, 0, 0L, 0, 0, 0,0),
            new Team(0L, 0, Region.EU, notEqualLeague, FIRST, BigInteger.ZERO, 0, 0L, 0, 0, 0,0),
            new Team(0L, 0, Region.EU, league, FIRST, BigInteger.ONE, 0, 0L, 0, 0, 0,0)
        };

        TestUtil.testUniqueness(team, equalTeam, (Object[]) notEqualTeams);
    }

    @Test
    public void testUid()
    {
        assertEquals
        (
            Team.uid(QueueType.LOTV_1V1, Region.EU, BigInteger.TEN, 1),
            Team.uid(QueueType.LOTV_1V1, Region.EU, BigInteger.TEN, 1)
        );
        assertNotEquals
        (
            Team.uid(QueueType.LOTV_1V1, Region.EU, BigInteger.TEN, 1),
            Team.uid(QueueType.LOTV_1V1, Region.EU, BigInteger.TEN, 2)
        );
    }

}
