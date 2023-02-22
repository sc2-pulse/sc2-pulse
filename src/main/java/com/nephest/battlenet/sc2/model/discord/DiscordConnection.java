// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import discord4j.common.util.Snowflake;
import javax.validation.constraints.NotNull;

public class DiscordConnection
{

    @NotNull
    private Snowflake id;

    @NotNull
    private String name;

    @NotNull
    private String type;

    @NotNull
    private Boolean verified;

    public DiscordConnection()
    {
    }

    public Snowflake getId()
    {
        return id;
    }

    public void setId(Snowflake id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Boolean getVerified()
    {
        return verified;
    }

    public void setVerified(Boolean verified)
    {
        this.verified = verified;
    }

}
