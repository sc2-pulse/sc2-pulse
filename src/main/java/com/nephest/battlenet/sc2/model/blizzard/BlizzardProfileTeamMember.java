// Copyright (C) 2021 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nephest.battlenet.sc2.model.Race;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class BlizzardProfileTeamMember
extends BlizzardPlayerCharacter
{

    @NotNull @Valid
    private Race favoriteRace;

    public BlizzardProfileTeamMember(){}

    public BlizzardProfileTeamMember(@NotNull @Valid Race favoriteRace)
    {
        this.favoriteRace = favoriteRace;
    }

    @Override
    @JsonAlias("displayName")
    public void setName(String name)
    {
        super.setName(name);
    }

    public Race getFavoriteRace()
    {
        return favoriteRace;
    }

    public void setFavoriteRace(Race favoriteRace)
    {
        this.favoriteRace = favoriteRace;
    }

}
