// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GOLD;
import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.PLATINUM;
import static com.nephest.battlenet.sc2.model.QueueType.HOTS_1V1;
import static com.nephest.battlenet.sc2.model.QueueType.WOL_1V1;
import static com.nephest.battlenet.sc2.model.TeamType.ARRANGED;
import static com.nephest.battlenet.sc2.model.TeamType.RANDOM;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class LeagueTest
{

    @Test
    public void testUniqueness()
    {
        League league = new League(0, 0, GOLD, WOL_1V1, ARRANGED);
        League equalLeague = new League(1, 0, GOLD, WOL_1V1, ARRANGED);

        League[] notEqualLeagues = new League[]
        {
            new League(0, 1, GOLD, WOL_1V1, ARRANGED),
            new League(0, 0, PLATINUM, WOL_1V1, ARRANGED),
            new League(0, 0, GOLD, HOTS_1V1, ARRANGED),
            new League(0, 0, GOLD, WOL_1V1, RANDOM),
        };

        TestUtil.testUniqueness(league, equalLeague, (Object[]) notEqualLeagues);
    }

}
