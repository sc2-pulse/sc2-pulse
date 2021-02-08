// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

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

        TestUtil.testUniqueness(character, equalsCharacter, notEqualCharacters);
    }

}
