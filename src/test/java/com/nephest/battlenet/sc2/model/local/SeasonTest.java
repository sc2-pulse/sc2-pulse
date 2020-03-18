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

import static com.nephest.battlenet.sc2.model.Region.EU;
import static com.nephest.battlenet.sc2.model.Region.US;

import org.junit.jupiter.api.Test;

import com.nephest.battlenet.sc2.util.TestUtil;

public class SeasonTest
{

    @Test
    public void testUniqueness()
    {
        Season season = new Season(0l, 0l, EU, 0, 0);
        Season equalSeason = new Season(1l, 0l, EU, 2, 3);
        Season[] notEqualSeasons = new Season[]
        {
            new Season(0l, 0l, US, 0, 0),
            new Season(0l, 1l, EU, 0, 0)
        };

        TestUtil.testUniqueness(season, equalSeason, notEqualSeasons);
    }

}
