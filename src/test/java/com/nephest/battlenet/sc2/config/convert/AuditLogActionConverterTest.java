// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.local.AuditLogEntry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class AuditLogActionConverterTest
{

    private final AuditLogEntryActionToStringConverter toString
        = new AuditLogEntryActionToStringConverter();
    private final StringToAuditLogEntryActionConverter fromString
        = new StringToAuditLogEntryActionConverter();

    @CsvSource
    ({
        "U, UPDATE",
        "I, INSERT",
        "D, DELETE",
        "T, TRUNCATE"
    })
    @ParameterizedTest
    public void testConvert(String in, AuditLogEntry.Action out)
    {
        assertEquals(out, fromString.convert(in));
        assertEquals(in, toString.convert(out));
    }

}
