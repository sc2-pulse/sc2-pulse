// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class StringToOffsetDateTimeConverterTest
{

    private final StringToOffsetDateTimeConverter converter = new StringToOffsetDateTimeConverter();

    @CsvSource
    ({
        "12345678, '1970-01-01T06:25:45.678+03:00'",
        "'2023-04-13T10:26:35.696681+03:00', '2023-04-13T10:26:35.696681+03:00'"
    })
    @ParameterizedTest
    public void testConvert(String input, OffsetDateTime expectedOutput)
    {
        assertTrue(converter.convert(input).isEqual(expectedOutput));
    }

}
