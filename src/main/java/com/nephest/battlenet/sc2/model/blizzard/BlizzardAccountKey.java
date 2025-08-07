// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.nephest.battlenet.sc2.model.util.DiscriminatedTag;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlizzardAccountKey
extends BlizzardKey
{

    public static final Pattern HREF_BATTLE_TAG_PATTERN = Pattern
        .compile("^https?://.*/data/sc2/character/(.+)-(\\d+)/.*$");

    public BlizzardAccountKey()
    {
    }

    public BlizzardAccountKey(String href)
    {
        super(href);
    }

    public DiscriminatedTag getBattleTag()
    {
        Matcher matcher = HREF_BATTLE_TAG_PATTERN.matcher(getHref());
        if(!matcher.matches()) throw new RuntimeException("Invalid key href: " + getHref());

        return new DiscriminatedTag(matcher.group(1), Long.parseLong(matcher.group(2)));
    }

}
