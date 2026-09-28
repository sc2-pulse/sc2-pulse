// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Sort;

public class SortToStringConverterTest
{

    private final SortToStringConverter converter
        = new SortToStringConverter();

    public static Stream<Arguments> convertArguments()
    {
        return Stream.of
        (
            Arguments.of(Sort.by(Sort.Direction.ASC, "name"), "name"),
            Arguments.of(Sort.by(Sort.Direction.DESC, "name"), "-name"),

            Arguments.of(Sort.unsorted(), null)
        );
    }

    @MethodSource("convertArguments")
    @ParameterizedTest
    public void testConvert(Sort sort, String expected)
    {
        assertEquals(expected, converter.convert(sort));
    }

}
