// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class MatchTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalTime = OffsetDateTime.now();
        Match match = new Match(1L, equalTime, BaseMatch.MatchType._1V1, 1, Region.EU);
        Match equalMatch = new Match(2L, equalTime, BaseMatch.MatchType._1V1, 1, Region.EU);

        Match[] notEqualMatches = new Match[]
        {
            new Match(1L, equalTime.minusDays(1), BaseMatch.MatchType._1V1, 1, Region.EU),
            new Match(1L, equalTime, BaseMatch.MatchType._2V2, 1, Region.EU),
            new Match(1L, equalTime, BaseMatch.MatchType._1V1, 2, Region.EU),
            new Match(1L, equalTime, BaseMatch.MatchType._1V1, 1, Region.US)
        };

        TestUtil.testUniqueness(match, equalMatch, (Object[]) notEqualMatches);
    }

}
