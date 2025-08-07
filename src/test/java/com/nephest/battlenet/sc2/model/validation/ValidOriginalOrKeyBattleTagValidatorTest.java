// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccountKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ValidOriginalOrKeyBattleTagValidatorTest
{

    private static final ValidOriginalOrKeyBattleTagValidator validator
        = new ValidOriginalOrKeyBattleTagValidator();

    @CsvSource
    ({
        "tag#1234, , true",
        "tag#1234#5678, https://eu.api.blizzard.com/data/sc2/character/tag-1234/5678, true",

        "tag#1234#5678, https://eu.api.blizzard.com/data/sc2/character/tag-asdf/5678, false",
        "tag#1234#5678, https://eu.api.blizzard.com/data/sc2/character/tag_1234/5678, false"
    })
    @ParameterizedTest
    public void testIsValid(String btag, String keyHref, boolean isValid)
    {
        BlizzardAccount account = new BlizzardAccount(1L, btag, new BlizzardAccountKey(keyHref));
        assertEquals(isValid, validator.isValid(account, null));
    }

    @Test
    public void whenValueOnValidationPathIsNull_thenNotValid()
    {
        BlizzardAccount nullAll = new BlizzardAccount(1L, null, null);
        assertFalse(validator.isValid(nullAll, null));

        BlizzardAccount nullHref = new BlizzardAccount
        (
            1L,
            "tag#1#2",
            new BlizzardAccountKey(null)
        );
        assertFalse(validator.isValid(nullHref, null));
    }

}
