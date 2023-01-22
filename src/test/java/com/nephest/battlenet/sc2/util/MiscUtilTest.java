// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MiscUtilTest
{

    public static final Function<Long, Long> SUBTRACTOR = l->l-1;

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

    @Test
    public void testParseLongRangeExcludingTo()
    {
        Range<Long> range = MiscUtil.parseRange("1 -    100", Long::parseLong, SUBTRACTOR, false);
        assertEquals(1, range.getMinimum());
        assertEquals(99, range.getMaximum());
    }

    @CsvSource
    ({
        "'1       -    100', 1, 100",
        "'-100--1', -100, -1",
        "'-1-100', -1, 100"
    })
    @ParameterizedTest
    public void testParseLongRangeIncludingTo(String input, long expectedFrom, long expectedTo)
    {
        Range<Long> range = MiscUtil.parseRange(input, Long::parseLong, SUBTRACTOR, true);
        assertEquals(expectedFrom, range.getMinimum());
        assertEquals(expectedTo, range.getMaximum());
    }

    @ValueSource(strings = {"1 100", "1-a", "-1", ".-1", "1-0", "-1 2", "1-2-3"})
    @ParameterizedTest
    public void whenInvalidInput_thenThrowRuntimeException(String input)
    {
        assertThrows
        (
            RuntimeException.class,
            ()->MiscUtil.parseRange(input, Long::parseLong, SUBTRACTOR, true)
        );
    }

    @CsvSource
    ({
        "'us', '\uD83C\uDDFA\uD83C\uDDF8'",
        "'UK', '\uD83C\uDDEC\uD83C\uDDE7'",
    })
    @ParameterizedTest
    public void testCountryCodeToEmoji(String in, String out)
    {
        assertEquals(out, MiscUtil.countryCodeToEmoji(in));
    }

    @ValueSource(strings = {"", "USA"})
    @ParameterizedTest
    public void whenInvalidCountryCodeInput_thenThrowRuntimeException(String input)
    {
        assertThrows
        (
            RuntimeException.class,
            ()->MiscUtil.countryCodeToEmoji(input)
        );
    }

}
