// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BlizzardAccountTest
{

    @CsvSource
    ({
        "tag#1234, true",
        "jsdnjfsdhfirtpo_571q#1234, true",
        "tag#1234567890, true",

        "tag#1234#5678, false",
        "#1234, false",
        "tag#tag, false",
        "tag, false",
        "1234, false"
    })
    @ParameterizedTest
    public void testBattleTagPattern(String btag, boolean valid)
    {
        assertEquals(valid, BlizzardAccount.BATTLE_TAG_PATTERN.matcher(btag).matches());
    }

    @Test
    public void whenBattleTagIsValid_thenReturnBattleTag()
    {
        BlizzardAccount account = new BlizzardAccount
        (
            1L,
            "tag#5678",
            BlizzardTestUtil.DEFAULT_ACCOUNT_KEY
        );
        assertEquals("tag#5678", account.getBattleTag());
    }

    @Test
    public void whenBattleTagIsInvalid_thenReturnKeyBattleTag()
    {
        BlizzardAccount account = new BlizzardAccount
        (
            1L,
            "tag#0987#5678",
            BlizzardTestUtil.DEFAULT_ACCOUNT_KEY
        );
        assertEquals("tag#1234", account.getBattleTag());
    }

}
