// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ProTeamTest
{

    @Test
    public void testUniqueness()
    {
        ProTeam proTeam = new ProTeam(1L, 1L, "name", "shortName");
        ProTeam equalProTeam = new ProTeam(2L, 2L, "nA Me ", "anotherShortName");
        ProTeam[] notEqualProTeams = new ProTeam[]
        {
            new ProTeam(1L, 1L, "nome", "shortName"),
        };
        TestUtil.testUniqueness(proTeam, equalProTeam, notEqualProTeams);
    }


}
