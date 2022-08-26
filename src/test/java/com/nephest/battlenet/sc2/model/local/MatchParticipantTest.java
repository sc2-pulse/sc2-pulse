// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

class MatchParticipantTest
{

    @Test
    public void testUniqueness()
    {
        MatchParticipant participant = new MatchParticipant(1L, 1L, BaseMatch.Decision.WIN);
        MatchParticipant equalParticipant = new MatchParticipant(1L, 1L, BaseMatch.Decision.LOSS);

        MatchParticipant[] notEqualParticipants = new MatchParticipant[]
        {
            new MatchParticipant(2L, 1L, BaseMatch.Decision.WIN),
            new MatchParticipant(1L, 2L, BaseMatch.Decision.WIN)
        };

        TestUtil.testUniqueness(participant, equalParticipant, (Object[]) notEqualParticipants);
    }

}
