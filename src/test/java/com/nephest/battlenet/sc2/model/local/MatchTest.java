// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

public class MatchTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalTime = OffsetDateTime.now();
        Match match = new Match(1L, equalTime, BaseMatch.MatchType._1V1, "map");
        Match equalMatch = new Match(2L, equalTime, BaseMatch.MatchType._1V1, "map");

        Match[] notEqualMatches = new Match[]
        {
            new Match(1L, equalTime.minusDays(1), BaseMatch.MatchType._1V1, "map"),
            new Match(1L, equalTime, BaseMatch.MatchType._2V2, "map"),
            new Match(1L, equalTime, BaseMatch.MatchType._1V1, "map2")
        };

        TestUtil.testUniqueness(match, equalMatch, notEqualMatches);
    }

}
