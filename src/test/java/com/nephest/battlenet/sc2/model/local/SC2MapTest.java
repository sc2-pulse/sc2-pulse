// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class SC2MapTest
{

    @Test
    public void testUniqueness()
    {
        SC2Map map = new SC2Map(0, "map1");
        SC2Map equalMap = new SC2Map(1, "map1");
        SC2Map[] notEqualMaps = new SC2Map[]
        {
            new SC2Map(0, "map2")
        };
        TestUtil.testUniqueness(map, equalMap, notEqualMaps);
    }

}
