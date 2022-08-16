// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import com.fasterxml.jackson.annotation.JsonAlias;
import discord4j.core.object.entity.User;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class DiscordUser
implements DiscordIdentity
{

    @NotNull
    private Long id;

    @NotNull
    private String name;

    @NotNull
    private Integer discriminator;

    public DiscordUser()
    {
    }

    public DiscordUser(Long id, String name, Integer discriminator)
    {
        this.id = id;
        this.name = name;
        this.discriminator = discriminator;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof DiscordUser)) {return false;}
        DiscordUser that = (DiscordUser) o;
        return getId().equals(that.getId());
    }

    @Override
    public String toString()
    {
        return "DiscordUser[" + id + ']';
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getId());
    }

    public static DiscordUser from(User user)
    {
        return new DiscordUser
        (
            user.getId().asLong(),
            user.getUsername(),
            Integer.valueOf(user.getDiscriminator())
        );
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @JsonAlias("username")
    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public Integer getDiscriminator()
    {
        return discriminator;
    }

    public void setDiscriminator(Integer discriminator)
    {
        this.discriminator = discriminator;
    }

}
