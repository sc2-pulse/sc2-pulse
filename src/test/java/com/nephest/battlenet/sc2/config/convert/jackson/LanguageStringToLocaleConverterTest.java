// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LanguageStringToLocaleConverterTest
{

    private LanguageStringToLocaleConverter converter;

    @BeforeEach
    public void beforeEach()
    {
        converter = new LanguageStringToLocaleConverter();
    }

    @CsvSource
    ({
        "en, en",
        "EN, en",
        "es, es",
        "ko, ko"
    })
    @ParameterizedTest
    public void testConvert(String in, String out)
    {
        assertEquals(out, converter.convert(in).toString());
    }

}
