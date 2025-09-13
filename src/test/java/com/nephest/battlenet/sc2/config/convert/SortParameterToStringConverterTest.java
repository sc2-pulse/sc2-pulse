// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SortParameterToStringConverterTest
{

    private final SortParameterToStringConverter converter
        = new SortParameterToStringConverter();

    public static Stream<Arguments> convertArguments()
    {
        return Stream.of
        (
            Arguments.of(new SortParameter("field", SortingOrder.ASC), "field"),
            Arguments.of(new SortParameter("field", SortingOrder.DESC), "-field")
        );
    }

    @MethodSource("convertArguments")
    @ParameterizedTest
    public void testConvert(SortParameter sort, String expected)
    {
        assertEquals(expected, converter.convert(sort));
    }

}
