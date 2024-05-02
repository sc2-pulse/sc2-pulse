// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import discord4j.common.util.Snowflake;
import jakarta.validation.constraints.NotNull;

public class IdentifiableEntity
implements GuildWrapper
{

    @NotNull
    private Snowflake id;

    public IdentifiableEntity()
    {
    }

    public IdentifiableEntity(@NotNull Snowflake id)
    {
        this.id = id;
    }

    public Snowflake getId()
    {
        return id;
    }

    public void setId(Snowflake id)
    {
        this.id = id;
    }

}
