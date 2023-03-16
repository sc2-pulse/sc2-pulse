// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RateLimitDataTest
{

    @Test
    public void testStringConstructor()
    {
        RateLimitData data = new RateLimitData(1, 2, "123.2");
        assertEquals(123200, data.getReset().toEpochMilli());
    }

    @CsvSource
    ({
        "1678897935.456, 1678897935456",
        "123.4, 123400",
        "123, 123000"
    })
    @ParameterizedTest
    public void testParseMillis(String input, long expectedResult)
    {
        assertEquals(expectedResult, RateLimitData.parseMillis(input));
        assertEquals(expectedResult, RateLimitData.parseInstant(input).toEpochMilli());
    }

}
