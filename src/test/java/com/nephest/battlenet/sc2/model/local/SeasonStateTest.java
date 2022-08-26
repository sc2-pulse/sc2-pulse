// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class SeasonStateTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalTimestamp = OffsetDateTime.now();
        SeasonState state = new SeasonState(1, equalTimestamp, 1, 1, 1);
        SeasonState equalState = new SeasonState(1, equalTimestamp, 0, 0, 0);
        SeasonState[] notEqualStates = new SeasonState[]
        {
            new SeasonState(0, equalTimestamp, 1, 1, 1),
            new SeasonState(1, equalTimestamp.minusSeconds(1), 1, 1, 1)
        };
        TestUtil.testUniqueness(state, equalState, (Object[]) notEqualStates);
    }

}
