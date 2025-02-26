// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import org.junit.jupiter.api.Test;

public class TeamLegacyUidTest
{

    private static final TeamLegacyUid LEGACY_UID = new TeamLegacyUid
    (
        QueueType.LOTV_1V1,
        TeamType.ARRANGED,
        Region.US,
        "1234"
    );
    private static final String LEGACY_UID_STRING = "201-0-1-1234";

    @Test
    public void testToPulseString()
    {
        assertEquals(LEGACY_UID_STRING, LEGACY_UID.toPulseString());
    }

    @Test
    public void testParse()
    {
        assertEquals(LEGACY_UID, TeamLegacyUid.parse(LEGACY_UID_STRING));
    }

}
