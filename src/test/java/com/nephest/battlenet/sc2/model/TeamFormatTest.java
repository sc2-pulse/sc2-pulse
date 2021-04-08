// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TeamFormatTest
{

    @CsvSource
    ({
        "1v1, _1V1",
        "2V2, _2V2",
        "3v3, _3V3",
        "4v4, _4V4",
        "ArChon, ARCHON"
    })
    @ParameterizedTest
    public void testFromName(String name, TeamFormat expected)
    {
        assertEquals(expected, TeamFormat.from(name));
    }

}
