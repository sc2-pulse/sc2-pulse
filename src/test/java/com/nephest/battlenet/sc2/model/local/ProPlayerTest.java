// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ProPlayerTest
{

    @Test
    public void testUniqueness()
    {
        ProPlayer proPlayer = new ProPlayer(1L, 2L, "nickname", "name");
        ProPlayer equalProPlayer = new ProPlayer(null, 2L, null, null);
        ProPlayer[] notEqualProPlayers = new ProPlayer[]
        {
            new ProPlayer(1L, 1L, "nickname", "name")
        };
        TestUtil.testUniqueness(proPlayer, equalProPlayer, (Object[]) notEqualProPlayers);
    }

}
