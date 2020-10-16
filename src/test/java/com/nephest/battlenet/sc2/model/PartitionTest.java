// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PartitionTest
{

    @CsvSource
    ({
        "https://www.battlenet.com.cn/oauth, CHINA",
        "https://eu.battle.net/oauth, GLOBAL",
        "https://us.battle.net/oauth, GLOBAL",
        "https://kr.battle.net/oauth, GLOBAL"
    })
    @ParameterizedTest
    public void testOfIssuer(String issuer, Partition expectedResult)
    throws MalformedURLException
    {
        assertEquals(expectedResult, Partition.ofIssuer(issuer));
        assertEquals(expectedResult, Partition.ofIssuer(new URL(issuer)));
    }

}
