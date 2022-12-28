// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import org.junit.jupiter.api.Test;

public class MarkdownUtilTest
{

    @Test
    public void testRenderLink()
    {
        PlayerCharacter character = new PlayerCharacter(1L, 1L, Region.EU, 1L, 1, "name#123");
        assertEquals("[name#123](url)", MarkdownUtil.renderLink(character, "url"));
    }

}
