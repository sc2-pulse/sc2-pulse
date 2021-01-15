// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;

import javax.validation.constraints.NotNull;

public class BlizzardPlayerCharacter
extends BasePlayerCharacter
{

    @NotNull
    private Long id;

    public BlizzardPlayerCharacter(){}

    public BlizzardPlayerCharacter(Long id, Integer realm, String name)
    {
        super(realm, name);
        this.id = id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    @Override
    @JsonAlias("displayName")
    public void setName(String name)
    {
        super.setName(name);
    }

}
