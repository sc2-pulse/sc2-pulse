/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import com.nephest.battlenet.sc2.util.TestUtil;

public class PlayerCharacterTest
{

    @Test
    public void testUniqueness()
    {
        PlayerCharacter character = new PlayerCharacter(0l, 0l, 0l, 0, "Name");
        PlayerCharacter equalsCharacter = new PlayerCharacter(1l, 0l, 0l, 1, "DiffName");

        PlayerCharacter[] notEqualCharacters = new PlayerCharacter[]
        {
            new PlayerCharacter(0l, 1l, 0l, 0, "Name"),
            new PlayerCharacter(0l, 0l, 1l, 0, "Name")
        };

        TestUtil.testUniqueness(character, equalsCharacter, notEqualCharacters);
    }

}
