// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ProPlayerServiceTest
{

    @CsvSource
    ({
        "'http://aligulac.com/players/485-Serral/', 485",
        "'http://aligulac.com/players/485', 485",
        "'http://aligulac.com/players/485/', 485",
        "'https://aligulac.com/players/48123576591445/', 48123576591445"
    })
    @ParameterizedTest
    public void testGetAligulacProfileId(String url, long expectedResult)
    {
        assertEquals(expectedResult, ProPlayerService.getAligulacProfileId(url));
    }

    @CsvSource
    ({
        "'http://aligulac.com/players/485-Serral/', 'http://aligulac.com/players/485'",
        "'http://aligulac.com/players/485', 'http://aligulac.com/players/485'",
        "'https://aligulac.com/players/48123576591445/', 'https://aligulac.com/players/48123576591445/'"
    })
    @ParameterizedTest
    public void testTrimAligulacUrl(String url, String expectedResult)
    {
        assertEquals(expectedResult, ProPlayerService.trimAligulacProfileLink(url));
    }


}
