// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PopulationStateTest
{

    @Test
    public void testEquality()
    {
        PopulationState state = new PopulationState(1, 1, 1, 1, 1);
        PopulationState equalState = new PopulationState(1, 0, 0, 0, 0);
        PopulationState[] notEqualsStates = new PopulationState[]
        {
            new PopulationState(0, 1, 1, 1, 1)
        };
        TestUtil.testUniqueness(state, equalState, notEqualsStates);
    }

}
