// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class SearchServiceTest
{

    @CsvSource
    ({
        "c, GENERAL",
        "[c, CLAN",
        "c#, BATTLE_TAG"
    })
    @ParameterizedTest
    public void testSearchTypeOf(String term, SearchService.SearchType expectedResult)
    {
        assertEquals(expectedResult, SearchService.SearchType.of(term));
    }

    @CsvSource
    ({
        ",",
        "'', ''",
        "[, ''",
        "[c, c",
        "[cl], cl"
    })
    @ParameterizedTest
    public void testExtractClanSearchTerm(String term, String expectedResult)
    {
        assertEquals(expectedResult, SearchService.extractClanTag(term));
    }

}
