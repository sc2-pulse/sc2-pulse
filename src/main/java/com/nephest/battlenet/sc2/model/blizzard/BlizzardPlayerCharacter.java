// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import jakarta.validation.constraints.NotNull;

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

    @JsonAlias("profileId")
    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

}
