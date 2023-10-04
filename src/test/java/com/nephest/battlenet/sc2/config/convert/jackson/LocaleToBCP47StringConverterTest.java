// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LocaleToBCP47StringConverterTest
{

    private LocaleToBCP47StringConverter converter;

    @BeforeEach
    public void beforeEach()
    {
        converter = new LocaleToBCP47StringConverter();
    }

    @CsvSource
    ({
        "en,,en",
        "EN,,en",
        "EN,US,en-US"
    })
    @ParameterizedTest
    public void testConvert(String language, String country, String out)
    {
        Locale locale = country != null ? new Locale(language, country) : new Locale(language);
        assertEquals(out, converter.convert(locale));
    }

}
