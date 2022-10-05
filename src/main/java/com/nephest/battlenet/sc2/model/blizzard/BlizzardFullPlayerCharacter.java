// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;

public class BlizzardFullPlayerCharacter
extends BlizzardPlayerCharacter
implements PlayerCharacterNaturalId
{

    private Region region;

    public BlizzardFullPlayerCharacter()
    {
    }

    public BlizzardFullPlayerCharacter(Long id, Integer realm, String name)
    {
        super(id, realm, name);
    }

    public BlizzardFullPlayerCharacter(Long id, Integer realm, String name, Region region)
    {
        super(id, realm, name);
        this.region = region;
    }

    @Override @JsonIgnore
    public Long getBattlenetId()
    {
        return getId();
    }

    @JsonAlias("regionId")
    public Region getRegion()
    {
        return region;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

}
