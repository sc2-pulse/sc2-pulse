// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.util.DiscriminatedTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BlizzardAccountKeyTest
{

    @CsvSource
    ({
        "https://eu.api.blizzard.com/data/sc2/character/tag-1234/5678?namespace=prod, true",
        "https://eu.api.blizzard.com/data/sc2/character/tag-1234/5678/1234, true",

        "https://eu.api.blizzard.com/data/sc2/character/tag-qwerty/5678?namespace=prod, false",
        "https://eu.api.blizzard.com/data/sc2/character/-1234/5678?namespace=prod, false",
        "https://eu.api.blizzard.com/data/sc2/character/tag_1234/5678?namespace=prod, false",
        "https://eu.api.blizzard.com/data/sc2/character/5678?namespace=prod, false",
    })
    @ParameterizedTest
    public void testKeyHrefBattleTagPattern(String href, boolean valid)
    {
        assertEquals(valid, BlizzardAccountKey.HREF_BATTLE_TAG_PATTERN.matcher(href).matches());
    }

    @Test
    public void testGetBattleTag()
    {
        assertEquals
        (
            new DiscriminatedTag("tag", 1234L),
            new BlizzardAccountKey("https://eu.api.blizzard.com/data/sc2/character/tag-1234/5678")
                .getBattleTag()
        );
    }


}
