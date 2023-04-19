// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @CsvSource
    ({
        "http://aligulac.com/players/6306-Gemini/, ALIGULAC",
        "https://discord.gg/sdfngjsdk, DISCORD",
        "battlenet:://starcraft/profile/2/4379857489798174891, BATTLE_NET",
        "nonExisting, UNKNOWN",
        ", UNKNOWN",
        "'    ', UNKNOWN"
    })
    @ParameterizedTest
    public void testFromBaseUrlPrefix(String url, SocialMedia expectedResult)
    {
        assertEquals(expectedResult, SocialMedia.fromBaseUrlPrefix(url));
    }

    @CsvSource
    ({
        "http://aligulac.com/players/6306-Gemini/, ALIGULAC",
        "nonExisting, UNKNOWN",
        ", UNKNOWN",
        "'    ', UNKNOWN"
    })
    @ParameterizedTest
    public void testFromBaseUserUrlPrefix(String url, SocialMedia expectedResult)
    {
        assertEquals(expectedResult, SocialMedia.fromBaseUserUrlPrefix(url));
    }

}
