// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class ClanTest
{

    @Test
    public void testUniqueness()
    {
        Clan clan = new Clan(null, "tag", Region.EU, "name");
        Clan equalClan = new Clan(1, "tag", Region.EU, "name2");
        Clan[] notEqualClans = new Clan[]
        {
            new Clan(null, "tag2", Region.EU, "name"),
            new Clan(null, "tag", Region.US, "name"),
        };

        TestUtil.testUniqueness(clan, equalClan, notEqualClans);
    }

}
