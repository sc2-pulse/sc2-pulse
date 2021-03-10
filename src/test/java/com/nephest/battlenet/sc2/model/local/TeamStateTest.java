// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

public class TeamStateTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalDate = OffsetDateTime.now();
        TeamState history = new TeamState(1L, equalDate, 1, 1, 1);
        TeamState equalHistory = new TeamState(1L, equalDate, 2, 2, 2);
        TeamState[] notEqualHistory = new TeamState[]
        {
            new TeamState(2L, equalDate, 1, 1, 1),
            new TeamState(1L, equalDate.minusDays(1), 1, 1, 1)
        };
        TestUtil.testUniqueness(history, equalHistory, notEqualHistory);
    }

}
