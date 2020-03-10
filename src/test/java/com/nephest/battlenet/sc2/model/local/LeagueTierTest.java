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
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.*;

public class LeagueTierTest
{

    @Test
    public void testUniqueness()
    {
        LeagueTier tier = new LeagueTier(0l, 0l, FIRST, 0, 0);
        LeagueTier equalTier = new LeagueTier(1l, 0l, FIRST, 1, 1);

        LeagueTier[] notEqualTiers = new LeagueTier[]
        {
            new LeagueTier(0l, 1l, FIRST, 0, 0),
            new LeagueTier(0l, 0l, SECOND, 0, 0),
        };

        TestUtil.testUniqueness(tier, equalTier, notEqualTiers);
    }

}
