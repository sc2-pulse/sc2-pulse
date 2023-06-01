// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class FormatUtilTest
{

    private final FormatUtil formatUtil = new FormatUtil();

    @CsvSource
    ({
        "P7D, '7 days'",
        "P7DT1H, '7 days 1 hour'"
    })
    @ParameterizedTest
    public void testFormatDurationWords(Duration duration, String expectedResult)
    {
        assertEquals(expectedResult, formatUtil.formatWords(duration));
    }

    @CsvSource
    ({
        "P7D,'7 days'",
        "P7DT1H, '7 days'"
    })
    @ParameterizedTest
    public void testFormatFirstDurationWord(Duration duration, String expectedResult)
    {
        assertEquals(expectedResult, formatUtil.formatFirstWord(duration));
    }


}
