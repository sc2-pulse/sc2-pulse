// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.discord.PulseMapping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PulseMappingTest
{

    @Test
    public void testImmutability()
    {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("1", new ArrayList<>(List.of(1, 2)));
        map.put("2", new ArrayList<>(List.of(4, 3)));
        PulseMapping<String, Integer> mapping = new PulseMapping<>
        (
            map,
            String::valueOf,
            ", "
        );

        map.put("3", List.of(3, 4));
        assertNull(mapping.getMappings().get("3"));

        map.get("1").add(5);
        assertEquals(List.of(1, 2), mapping.getMappings().get("1"));

        assertEquals("1, 2, 4, 3", mapping.getString());

        assertThrows
        (
            UnsupportedOperationException.class,
            ()->mapping.getMappings().put("3", List.of())
        );
        assertThrows
        (
            UnsupportedOperationException.class,
            ()->mapping.getMappings().get("1").add(2)
        );
    }

}
