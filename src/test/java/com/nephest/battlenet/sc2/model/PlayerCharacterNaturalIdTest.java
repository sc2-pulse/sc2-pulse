// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PlayerCharacterNaturalIdTest
{

    @Test
    public void testStaticCreatorEquality()
    {
        Region region = Region.EU;
        Integer realm = 1;
        Long id = 2L;
        TestUtil.testUniqueness
        (
            PlayerCharacterNaturalId.of(region, realm, id),
            PlayerCharacterNaturalId.of(region, realm, id)
        );
    }



    @CsvSource
    ({
        "1-S2-2-33, US, 2, 33",
        "2-S2-11-12345, EU, 11, 12345"
    })
    @ParameterizedTest
    public void testToonHandle(String toonHandle, Region region, int realm, long id)
    {
        assertTrue(toonHandle.matches(PlayerCharacterNaturalId.TOON_HANDLE_REGEXP));
        PlayerCharacterNaturalId originalId = PlayerCharacterNaturalId.of(region, realm, id);
        PlayerCharacterNaturalId toonId = PlayerCharacterNaturalId.ofToonHandle(toonHandle);
        assertEquals(originalId, toonId);
        assertEquals(toonHandle, originalId.toToonHandle());
        assertEquals(toonHandle, toonId.toToonHandle());
    }

    @CsvSource
    ({
        "1-s2-2-33, The second section must be S2",
        "9-S2-2-33, Invalid id",
        "1-S2-qwerty-33, For input string: \"qwerty\"",
        "1-S2-2-qwerty, For input string: \"qwerty\"",
    })
    @ParameterizedTest
    public void testInvalidToonHandle(String toonHandle, String errorMsg)
    {
        assertFalse(toonHandle.matches(PlayerCharacterNaturalId.TOON_HANDLE_REGEXP));
        assertThrows
        (
            IllegalArgumentException.class,
            ()->PlayerCharacterNaturalId.ofToonHandle(toonHandle),
            errorMsg
        );
    }

}
