// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;

public class BlizzardLegacyProfile
extends BlizzardPlayerCharacter
{

    private String clanTag, clanName;

    @Override
    @JsonAlias("displayName")
    public void setName(String name)
    {
        super.setName(name);
    }

    public String getClanTag()
    {
        return clanTag;
    }

    public void setClanTag(String clanTag)
    {
        this.clanTag = clanTag;
    }

    public String getClanName()
    {
        return clanName;
    }

    public void setClanName(String clanName)
    {
        this.clanName = clanName;
    }

}
