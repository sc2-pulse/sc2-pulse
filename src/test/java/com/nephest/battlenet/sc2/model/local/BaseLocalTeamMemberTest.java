// Copyright (C) 2021 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BaseLocalTeamMemberTest
{

    @Test
    public void testRaceMethods()
    {
        BaseLocalTeamMember member = new BaseLocalTeamMember(1, 2, 5, null);
        assertEquals(Race.ZERG, member.getFavoriteRace());
        assertEquals(1, member.getGamesPlayed(Race.TERRAN));
        assertEquals(2, member.getGamesPlayed(Race.PROTOSS));
        assertEquals(5, member.getGamesPlayed(Race.ZERG));
        assertNull(member.getGamesPlayed(Race.RANDOM));
    }

}
