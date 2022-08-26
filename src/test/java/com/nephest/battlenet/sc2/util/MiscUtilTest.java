// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MiscUtilTest
{

    @CsvSource
    ({
        "2022-02-17T12:00:00, 0.0",
        "2022-02-17T12:15:00, 0.25",
        "2022-02-17T12:30:00, 0.50",
        "2022-02-17T12:45:00, 0.75"
    })
    @ParameterizedTest
    public void testHourProgress(LocalDateTime dateTime, double expectedResult)
    {
        assertEquals(expectedResult, MiscUtil.getHourProgress(dateTime));
    }

    @CsvSource
    ({
        "2022-02-17T12:00:00, 3600",
        "2022-02-17T12:45:00, 900",
        "2022-02-17T12:59:59, 1",
    })
    @ParameterizedTest
    public void testUntilNextHour(LocalDateTime dateTime, long seconds)
    {
        Duration expectedResult = Duration.ofSeconds(seconds);
        assertEquals(expectedResult, MiscUtil.untilNextHour(dateTime));
    }

    @CsvSource
    ({
        "0, 1",
        "-1, 1",
        "9999, 4",
        "-1234, 4",
        Integer.MAX_VALUE + ", 10",
        Integer.MIN_VALUE + ", 10"
    })
    @ParameterizedTest
    public void testStringLength(int i, int expectedLength)
    {
        assertEquals(expectedLength, MiscUtil.stringLength(i));
    }


}
