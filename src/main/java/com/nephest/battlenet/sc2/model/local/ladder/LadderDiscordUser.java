// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.discord.DiscordIdentity;
import com.nephest.battlenet.sc2.model.local.DiscordUserMeta;

public class LadderDiscordUser
{

    private DiscordIdentity user;
    private DiscordUserMeta meta;

    public LadderDiscordUser()
    {
    }

    public LadderDiscordUser(DiscordIdentity user, DiscordUserMeta meta)
    {
        this.user = user;
        this.meta = meta;
    }

    public DiscordIdentity getUser()
    {
        return user;
    }

    public void setUser(DiscordIdentity user)
    {
        this.user = user;
    }

    public DiscordUserMeta getMeta()
    {
        return meta;
    }

    public void setMeta(DiscordUserMeta meta)
    {
        this.meta = meta;
    }

}
