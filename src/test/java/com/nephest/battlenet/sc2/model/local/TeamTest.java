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

import org.mockito.*;
import org.mockito.stubbing.*;
import org.mockito.invocation.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.model.*;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.*;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        League equalLeague = mock(League.class);
        Team team = new Team
        (
            0l, 0l, Region.EU, equalLeague, FIRST,
            0l, BigInteger.valueOf(0),
            0l, 0, 0, 0, 0
        );
        Team equalTeam = new Team
        (
            1l, 1l, Region.US, mock(League.class), SECOND,
            0l, BigInteger.valueOf(0),
            1l, 1, 1, 1, 1
        );

        Team[] notEqualTeams = new Team[]
        {
            new Team
            (
                0l, 0l, Region.EU, equalLeague, FIRST,
                1l, BigInteger.valueOf(0),
                0l, 0, 0, 0, 0
            ),
            new Team
            (
                0l, 0l, Region.EU, equalLeague, FIRST,
                0l, BigInteger.valueOf(1),
                0l, 0, 0, 0, 0
            )
        };

        TestUtil.testUniqueness(team, equalTeam, notEqualTeams);
    }

}
