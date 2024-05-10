// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PendingLadderDataTest
{

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testCopyConstructor(boolean immutable)
    {
        PendingLadderData src = new PendingLadderData();
        src.getStatsUpdates().addAll(List.of(1, 3));
        src.getTeams().addAll(List.of(11L, 12L));
        src.getCharacters().addAll(List.of(
            new PlayerCharacter(1L, 2L, Region.EU, 3L, 4, "name#1"),
            new PlayerCharacter(2L, 3L, Region.US, 4L, 5, "name#5")
        ));
        PendingLadderData copy = immutable
            ? PendingLadderData.immutableCopy(src)
            : new PendingLadderData(src);
        Assertions.assertThat(copy)
            .usingRecursiveComparison()
            .isEqualTo(src);

        //copied collections are not connected to the original ones
        src.getStatsUpdates().add(99);
        assertTrue(src.getStatsUpdates().contains(99));
        assertFalse(copy.getStatsUpdates().contains(99));

        if(immutable)
        {
            assertThrows(UnsupportedOperationException.class, ()->copy.getStatsUpdates().clear());
            assertThrows(UnsupportedOperationException.class, ()->copy.getTeams().clear());
            assertThrows(UnsupportedOperationException.class, ()->copy.getCharacters().clear());
        }
        else
        {
            src.clear();
            assertTrue(src.getStatsUpdates().isEmpty());
            assertTrue(src.getTeams().isEmpty());
            assertTrue(src.getCharacters().isEmpty());
            assertFalse(copy.getStatsUpdates().isEmpty());
            assertFalse(copy.getTeams().isEmpty());
            assertFalse(copy.getCharacters().isEmpty());
        }
    }

}
