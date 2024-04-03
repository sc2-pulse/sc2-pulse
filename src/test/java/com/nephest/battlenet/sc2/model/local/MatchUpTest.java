// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.TERRAN;
import static com.nephest.battlenet.sc2.model.Race.ZERG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MatchUpTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new MatchUp(List.of(TERRAN, PROTOSS), List.of(PROTOSS, ZERG)),
            new MatchUp(List.of(TERRAN, PROTOSS), List.of(PROTOSS, ZERG)),

            new MatchUp(List.of(PROTOSS, TERRAN), List.of(PROTOSS, ZERG)),
            new MatchUp(List.of(TERRAN, PROTOSS), List.of(ZERG, PROTOSS))
        );
    }

    @Test
    public void testSize()
    {
        MatchUp matchUp = new MatchUp(List.of(TERRAN, PROTOSS), List.of(PROTOSS, ZERG));
        assertEquals(2, matchUp.getTeamSize());
        assertEquals(4, matchUp.getSize());
    }

    @Test
    public void whenMatchUpOfDifferentSizes_thenThrowException()
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->new MatchUp(List.of(TERRAN, PROTOSS), List.of(ZERG)),
            "Match-up sizes are not equal"
        );
    }

    @Test
    public void whenMatchUpIsEmpty_thenThrowException()
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->new MatchUp(List.of(), List.of()),
            "Empty match-up"
        );
    }


}
