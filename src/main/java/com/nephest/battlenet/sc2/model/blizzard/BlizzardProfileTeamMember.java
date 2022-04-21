// Copyright (C) 2020-2022 Oleksandr Masniuk
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

    private String clanTag;

    public BlizzardProfileTeamMember(){}

    public BlizzardProfileTeamMember(@NotNull @Valid Race favoriteRace)
    {
        this.favoriteRace = favoriteRace;
    }

    public BlizzardProfileTeamMember(Long id, Integer realm, String name, Race favoriteRace, String clanTag)
    {
        super(id, realm, name);
        this.favoriteRace = favoriteRace;
        this.clanTag = clanTag;
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

    public String getClanTag()
    {
        return clanTag;
    }

    public void setClanTag(String clanTag)
    {
        this.clanTag = clanTag;
    }

}
