// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class AuditLogEntryTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = SC2Pulse.offsetDateTime();
        OffsetDateTime notEqualOdt = equalOdt.minusSeconds(1);
        TestUtil.testUniqueness
        (
            new AuditLogEntry
            (
                1L,
                equalOdt,
                "schema1",
                "table1",
                AuditLogEntry.Action.UPDATE,
                "data1",
                "changedData1",
                2L
            ),
            new AuditLogEntry
            (
                1L,
                notEqualOdt,
                "schema2",
                "table2",
                AuditLogEntry.Action.INSERT,
                "data2",
                "changedData2",
                3L
            ),
            new AuditLogEntry
            (
                2L,
                equalOdt,
                "schema1",
                "table1",
                AuditLogEntry.Action.UPDATE,
                "data1",
                "changedData1",
                2L
            )
        );
    }

}
