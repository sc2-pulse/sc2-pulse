// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class EvidenceVoteTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalTime = SC2Pulse.offsetDateTime();
        EvidenceVote vote = new EvidenceVote(0, equalTime, 0L, true, equalTime);
        EvidenceVote equalVote = new EvidenceVote(0, equalTime.minusSeconds(1), 0L, false, equalTime.minusSeconds(1));
        EvidenceVote[] notEqualVotes = new EvidenceVote[]
        {
            new EvidenceVote(1, equalTime, 0L, true, equalTime),
            new EvidenceVote(0, equalTime, 1L, true, equalTime)
        };

        TestUtil.testUniqueness(vote, equalVote, (Object[]) notEqualVotes);
    }

}
