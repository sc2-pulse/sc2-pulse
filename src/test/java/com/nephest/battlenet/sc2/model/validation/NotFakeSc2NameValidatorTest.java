// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class NotFakeSc2NameValidatorTest
{

    private final NotFakeSc2NameValidator validator = new NotFakeSc2NameValidator();

    @CsvSource
    ({
        "'f#', false",
        "'', true",
        ",true",
        "'f1#', true"
    })
    @ParameterizedTest
    public void testValidate(String in, boolean out)
    {
        assertEquals(out, validator.isValid(in, null));
    }

}
