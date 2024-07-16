// Copyright (C) 2020-2024 Oleksandr Masniuk
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
        "battlenet:://starcraft/profile/2/4379857489798174891, BATTLE_NET",
        "battlenet://starcraft/profile/2/4379857489798174891, BATTLE_NET",
        "starcraft:://profile/2/4379857489798174891, BATTLE_NET",
        "starcraft-something://profile/2/4379857489798174891, BATTLE_NET",
        "nonExisting, UNKNOWN",
        ", UNKNOWN",
        "'    ', UNKNOWN"
    })
    @ParameterizedTest
    public void testFromLaxUserUrl(String url, SocialMedia expectedResult)
    {
        assertEquals(expectedResult, SocialMedia.fromLaxUserUrl(url));
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

    @CsvSource
    ({
        "ALIGULAC, http://aligulac.com/players",
        "DISCORD, https://discord.gg",
        "UNKNOWN,"
    })
    @ParameterizedTest
    public void testGetBaseUserOrBaseUrl(SocialMedia type, String expectedResult)
    {
        assertEquals(expectedResult, type.getBaseUserOrBaseUrl());
    }

    @CsvSource
    ({
        "http://aligulac.com/players/6306-Gemini/, ALIGULAC, 6306-Gemini/",
        "battlenet:://starcraft/profile/1/2, BATTLE_NET, 1/2",
        "battlenet://starcraft/profile/2/3, BATTLE_NET,",
        "starcraft:://profile/3/4, BATTLE_NET,",
        "starcraft-something://profile/4/5, BATTLE_NET,",
        "nonExisting, UNKNOWN,",
        ", UNKNOWN,",
        "'    ', UNKNOWN,"
    })
    @ParameterizedTest
    public void testGetBaseRelativeUserUrl(String url, SocialMedia sm, String relativeUserUrl)
    {
        assertEquals
        (
            relativeUserUrl,
            sm.getBaseRelativeUserUrl(url).orElse(null)
        );
    }

    @CsvSource
    ({
        "http://aligulac.com/players/6306-Gemini/, ALIGULAC, 6306-Gemini/",
        "battlenet:://starcraft/profile/1/2, BATTLE_NET, 1/2",
        "battlenet://starcraft/profile/2/3, BATTLE_NET, 2/3",
        "starcraft:://profile/3/4, BATTLE_NET, 3/4",
        "starcraft-something://profile/4/5, BATTLE_NET, 4/5",
        "nonExisting, UNKNOWN,",
        ", UNKNOWN,",
        "'    ', UNKNOWN,"
    })
    @ParameterizedTest
    public void testGetRelativeLaxUserUrl(String url, SocialMedia sm, String relativeUserUrl)
    {
        assertEquals
        (
            relativeUserUrl,
            sm.getRelativeLaxUserUrl(url).orElse(null)
        );
    }

}
