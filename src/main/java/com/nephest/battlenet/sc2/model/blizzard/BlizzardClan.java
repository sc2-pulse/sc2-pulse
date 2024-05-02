// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseClan;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardClan
extends BaseClan
{

    @NotNull
    private Long id;

    @NotNull
    @JsonAlias("clan_name")
    private String name;

    public BlizzardClan(){}

    public BlizzardClan(Long id, String tag, String name)
    {
        super(tag);
        this.id = id;
        this.name = name;
    }

    @Override
    @JsonAlias("clan_tag")
    public void setTag(String tag)
    {
        super.setTag(tag);
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
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

}
