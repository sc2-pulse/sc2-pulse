// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.FIRST;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.SECOND;

public class LeagueTierTest
{

    @Test
    public void testUniqueness()
    {
        LeagueTier tier = new LeagueTier(0, 0, FIRST, 0, 0);
        LeagueTier equalTier = new LeagueTier(1, 0, FIRST, 1, 1);

        LeagueTier[] notEqualTiers = new LeagueTier[]
        {
            new LeagueTier(0, 1, FIRST, 0, 0),
            new LeagueTier(0, 0, SECOND, 0, 0),
        };

        TestUtil.testUniqueness(tier, equalTier, notEqualTiers);
    }

}
