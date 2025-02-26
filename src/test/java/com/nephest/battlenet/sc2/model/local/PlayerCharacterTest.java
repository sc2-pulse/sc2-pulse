// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PlayerCharacterTest
{

    @Test
    public void testUniqueness()
    {
        PlayerCharacter character = new PlayerCharacter(0L, 0L, Region.EU, 0L, 0, "Name");
        PlayerCharacter equalsCharacter = new PlayerCharacter(1L, 1L, Region.EU, 0L, 0, "DiffName");

        PlayerCharacter[] notEqualCharacters = new PlayerCharacter[]
        {
            new PlayerCharacter(0L, 0L, Region.US, 0L, 0, "Name"),
            new PlayerCharacter(0L, 0L, Region.EU, 1L, 0, "Name"),
            new PlayerCharacter(0L, 0L, Region.EU, 0L, 1, "Name")
        };

        TestUtil.testUniqueness(character, equalsCharacter, (Object[]) notEqualCharacters);
    }

    @Test
    public void testGenerateProfileSuffix()
    {
        PlayerCharacter character = new PlayerCharacter(1L, 2L, Region.EU, 3L, 4, "name#1");
        assertEquals("/2/4/3", character.generateProfileSuffix());
    }

    @CsvSource
    ({
        "1, true",
        "0, false",
        ", false",
        "-1, false",
        "2, false"
    })
    @ParameterizedTest
    public void testIsFakeDiscriminator(Integer discriminator, boolean expected)
    {
        assertEquals(expected, PlayerCharacter.isFakeDiscriminator(discriminator));
    }

}
