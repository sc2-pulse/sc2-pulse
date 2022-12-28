// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;

public final class MarkdownUtil
{

    public static String renderLink(PlayerCharacter playerCharacter, String url)
    {
        return "[" + playerCharacter.getName() + "](" + url + ")";
    }

}
