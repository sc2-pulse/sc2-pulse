// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class CountryAlpha2ValidatorTest
{

    private final CountryAlpha2Validator validator = new CountryAlpha2Validator();

    @CsvSource
    ({
        ", true",
        "US, true",
        "' ', false",
        "ASD, false",
        "12, false",
    })
    @ParameterizedTest
    public void testIsValid(String input, boolean expectedResult)
    {
        assertEquals(expectedResult, validator.isValid(input, null));
    }

}
