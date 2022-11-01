// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

public class PulseMappingsTest
{

    @Test
    public void testValues()
    {
        PulseMappings<Integer> mappings = new PulseMappings<>
        (
            Map.of(Region.EU, List.of(1, 2, 3)),
            Map.of(BaseLeague.LeagueType.BRONZE, List.of(4, 5)),
            Map.of(Race.TERRAN, List.of(5, 5, 6)),
            Map.of(Range.between(1, 2), List.of(7)),
            String::valueOf,
            ", "
        );

        assertEquals
        (
            List.of(1, 2, 3, 4, 5, 5, 5, 6, 7),
            mappings.valuesStream().collect(Collectors.toList())
        );
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), mappings.getValues());
    }

    @Test
    public void whenEmpty_thenReturnTrue()
    {
        PulseMappings<Integer> mappings = new PulseMappings<>
        (
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            String::valueOf,
            ", "
        );
        assertTrue(mappings.isEmpty());
    }

    @Test
    public void whenNotEmpty_thenReturnFalse()
    {
        PulseMappings<Integer> mappings = new PulseMappings<>
        (
            Map.of(),
            Map.of(BaseLeague.LeagueType.BRONZE, List.of(1, 2)),
            Map.of(),
            Map.of(),
            String::valueOf,
            ", "
        );
        assertFalse(mappings.isEmpty());
    }

    @Test
    public void testImmutability()
    {
        PulseMappings<Integer> mappings = new PulseMappings<>
        (
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            String::valueOf,
            ", "
        );
        assertThrows(UnsupportedOperationException.class, ()->mappings.getValues().add(1));
        assertThrows(UnsupportedOperationException.class, ()->mappings.getValues().remove(1));
    }

}
