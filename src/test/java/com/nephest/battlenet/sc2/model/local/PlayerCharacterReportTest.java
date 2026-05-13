// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.local.PlayerCharacterReport.PlayerCharacterReportType.CHEATER;
import static com.nephest.battlenet.sc2.model.local.PlayerCharacterReport.PlayerCharacterReportType.LINK;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class PlayerCharacterReportTest
{

    @Test
    public void testEquality()
    {
        OffsetDateTime equalDateTime = SC2Pulse.offsetDateTime();
        PlayerCharacterReport report = new PlayerCharacterReport(0, 0L, 0L,
            CHEATER, false, false, equalDateTime, false);
        PlayerCharacterReport equalReport = new PlayerCharacterReport(1, 0L, 0L,
            CHEATER, true, true, equalDateTime.plusSeconds(1), true);
        PlayerCharacterReport[] notEqualReports = new PlayerCharacterReport[]
        {
            new PlayerCharacterReport(0, 1L, 0L, CHEATER, false, false, equalDateTime, false),
            new PlayerCharacterReport(0, 0L, 1L, CHEATER, false, false, equalDateTime, false),
            new PlayerCharacterReport(0, 0L, 0L, LINK, false, false, equalDateTime, false)
        };

        TestUtil.testUniqueness(report, equalReport, (Object[]) notEqualReports);
    }

}
