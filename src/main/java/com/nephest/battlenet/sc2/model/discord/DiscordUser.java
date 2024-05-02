// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import com.fasterxml.jackson.annotation.JsonAlias;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class DiscordUser
implements DiscordIdentity
{

    @NotNull
    private Snowflake id;

    @NotNull
    private String name;

    @NotNull
    private Integer discriminator;

    public DiscordUser()
    {
    }

    public DiscordUser(Snowflake id, String name, Integer discriminator)
    {
        this.id = id;
        this.name = name;
        this.discriminator = discriminator;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof DiscordUser that)) {return false;}
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

    /**
     * The 0 discriminator is a special case that is used for username migration where
     * discriminators are removed in favor of unique usernames. 0 discriminator means user has
     * migrated to the new system and has no discriminator, so null is used instead.
     *
     * @param user Discord4j user
     * @return local pulse entity
     */
    public static DiscordUser from(User user)
    {
        @SuppressWarnings("deprecation")
        Integer discriminator = user.getDiscriminator() == null || user.getDiscriminator().equals("0")
            ? null
            : Integer.valueOf(user.getDiscriminator());
        return new DiscordUser
        (
            user.getId(),
            user.getUsername(),
            discriminator
        );
    }

    public Snowflake getId()
    {
        return id;
    }

    public void setId(Snowflake id)
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
