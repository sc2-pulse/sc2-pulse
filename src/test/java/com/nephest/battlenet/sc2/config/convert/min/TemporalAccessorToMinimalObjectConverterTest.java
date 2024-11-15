// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TemporalAccessorToMinimalObjectConverterTest
{

    private final TemporalAccessorToMinimalObjectConverter converter
        = new TemporalAccessorToMinimalObjectConverter();

    public static Stream<Arguments> testConvert()
    {
        Instant instant = Instant.now();
        long ts = instant.getEpochSecond();
        return Stream.of
        (
            Arguments.of(instant, ts),
            Arguments.of(OffsetDateTime.ofInstant(instant, ZoneOffset.systemDefault()), ts),
            Arguments.of(ZonedDateTime.ofInstant(instant, ZoneOffset.systemDefault()), ts),

            //unsupported accessor, return the src object
            Arguments.of(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1))
        );
    }


    @ParameterizedTest
    @MethodSource
    public void testConvert(TemporalAccessor src, Object expected)
    {
        assertEquals(expected, converter.convert(src));
    }

}
