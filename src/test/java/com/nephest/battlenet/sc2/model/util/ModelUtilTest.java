// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ModelUtilTest
{

    @CsvSource
    ({
        "' asd aa   fdf f  ', 'asd aa fdf f'",
        "' ', ''",
        "'', ''",
        "a, a",
        "'a a', 'a a'"
    })
    @ParameterizedTest
    public void testTrimSingleSpace(String in, String out)
    {
        assertEquals(out, ModelUtil.trimSingleSpace(in));
    }

    @CsvSource
    ({
        "' asd aa   fdf f  ', 'asd aa fdf f'",
        "' ',",
        "'',",
        ",",
        "a, a",
        "'a a', 'a a'",
    })
    @ParameterizedTest
    public void trimSingleSpaceNotBlank(String in, String out)
    {
        assertEquals(out, ModelUtil.trimSingleSpaceNotBlank(in));
    }

    @CsvSource
    ({
        "'', false",
        "'                                 ', false",
        "'                           d      ', true",
        "'d                             d                         1', true"
    })
    @ParameterizedTest
    public void testTrimmedNotBlankRegexp(String input, boolean expectedResult)
    {
        assertEquals
        (
            expectedResult,
            Pattern.matches(ModelUtil.VALIDATION_REGEXP_TRIMMED_NOT_BLANK, input)
        );
    }


}
