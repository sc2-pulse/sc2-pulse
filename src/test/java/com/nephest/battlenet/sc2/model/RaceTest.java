// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RaceTest
{

    @CsvSource
    ({
        "terraN, TERRAN",
        "proToss, PROTOSS",
        "Zerg, ZERG",
        "RANDOM, RANDOM"
    })
    @ParameterizedTest
    public void testFromName(String name, Race expectedResult)
    {
        assertEquals(expectedResult, Race.optionalFrom(name).get());
        assertEquals(expectedResult, Race.from(name));
    }

}
