// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ProPlayerTest
{

    @Test
    public void testUniqueness()
    {
        ProPlayer proPlayer = new ProPlayer(1L, new byte[]{0x1, 0x2}, "nickname", "name");
        ProPlayer equalProPlayer = new ProPlayer(null, new byte[]{0x1, 0x2}, null, null);
        ProPlayer[] notEqualProPlayers = new ProPlayer[]
        {
            new ProPlayer(1L, new byte[]{0x2, 0x2}, "nickname", "name")
        };
        TestUtil.testUniqueness(proPlayer, equalProPlayer, (Object[]) notEqualProPlayers);
    }

}
