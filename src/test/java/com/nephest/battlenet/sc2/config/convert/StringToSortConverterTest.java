// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class StringToSortConverterTest
{

    private StringToSortConverter converter;

    @BeforeEach
    void setUp()
    {
        converter = new StringToSortConverter();
    }

    public static Stream<Arguments> conversionArguments()
    {
        return Stream.of
        (
            Arguments.of("name", Sort.by(Sort.Direction.ASC, "name")),
            Arguments.of("+name", Sort.by(Sort.Direction.ASC, "name")),
            Arguments.of("-name", Sort.by(Sort.Direction.DESC, "name")),

            Arguments.of("name:asc", Sort.by(Sort.Direction.ASC, "name")),
            Arguments.of("name:desc", Sort.by(Sort.Direction.DESC, "name")),

            Arguments.of("   -name   ", Sort.by(Sort.Direction.DESC, "name"))
        );
    }

    @MethodSource("conversionArguments")
    @ParameterizedTest
    public void testConversion(String string, Sort expected)
    {
        assertEquals(expected, converter.convert(string));
    }

    @Test
    void shouldThrowOnEmptyString()
    {
        assertThrows(IllegalArgumentException.class, ()->converter.convert("  "));
    }

    @Test
    void shouldThrowOnInvalidSuffix()
    {
        assertThrows(IllegalArgumentException.class, ()->converter.convert("name:wrong"));
    }

    @Test
    void shouldConvertMultipleFieldsInOrder()
    {
        Sort sort = converter.convert(" name , -age , email:DESC ");
        assertEquals
        (
            Sort.by
            (
                new Sort.Order(Sort.Direction.ASC, "name"),
                new Sort.Order(Sort.Direction.DESC, "age"),
                new Sort.Order(Sort.Direction.DESC, "email")
            ),
            sort
        );
    }

    @ValueSource(strings = {
        ",name",
        "name,,age",
        "name,",
        "name,age:wrong"
    })
    @ParameterizedTest
    void shouldRejectEmptyOrInvalidFieldInList(String input)
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->converter.convert(input)
        );
    }

}
