// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.Race;
import java.util.AbstractMap;
import java.util.List;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testGamesPlayedMap()
    {
        BaseLocalTeamMember member = new BaseLocalTeamMember(10, 2, 5, null);
        assertEquals
        (
            List.of
            (
                new AbstractMap.SimpleEntry<>(Race.TERRAN, 10),
                new AbstractMap.SimpleEntry<>(Race.ZERG, 5),
                new AbstractMap.SimpleEntry<>(Race.PROTOSS, 2)
            ),
            member.getRaceGames().entrySet().stream().toList()
        );

        member.setGamesPlayed(Race.RANDOM, 2);
        member.setGamesPlayed(Race.TERRAN, 3);

        assertEquals
        (
            List.of
            (

                new AbstractMap.SimpleEntry<>(Race.ZERG, 5),
                new AbstractMap.SimpleEntry<>(Race.TERRAN, 3),
                new AbstractMap.SimpleEntry<>(Race.PROTOSS, 2),
                new AbstractMap.SimpleEntry<>(Race.RANDOM, 2)
            ),
            member.getRaceGames().entrySet().stream().toList()
        );

        //unmodifiable
        assertThrows
        (
            UnsupportedOperationException.class,
            ()->member.getRaceGames().remove(Race.TERRAN)
        );
        assertThrows
        (
            UnsupportedOperationException.class,
            ()->member.getRaceGames().put(Race.TERRAN, 99)
        );
    }

}
