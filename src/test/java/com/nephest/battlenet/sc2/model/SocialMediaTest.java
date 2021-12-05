// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocialMediaTest
{

    @CsvSource
    ({
        "http://aligulac.com/players/6306-Gemini/, 6306",
        "http://aligulac.com/players/6306-Gemini, 6306"
    })
    @ParameterizedTest
    public void testGetAligulacIdFromUrl(String url, long expectedId)
    {
        assertEquals(expectedId, SocialMedia.getAligulacIdFromUrl(url));
    }

}
