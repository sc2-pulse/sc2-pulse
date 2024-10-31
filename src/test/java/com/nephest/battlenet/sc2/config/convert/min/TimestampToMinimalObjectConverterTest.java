// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TimestampToMinimalObjectConverterTest
{

    private final TimestampToMinimalObjectConverter converter
        = new TimestampToMinimalObjectConverter();

    @CsvSource
    ({
        "1000, 1",
        "10000, 10",
        "100, 0",
    })
    @ParameterizedTest
    public void testConvert(long timestamp, long expectedValue)
    {
        assertEquals(expectedValue, converter.convert(new Timestamp(timestamp)));
    }

}
