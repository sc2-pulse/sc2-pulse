// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

public class BlizzardLegacyProfile
extends BlizzardPlayerCharacter
{

    private final Instant createdAt = Instant.now();

    private String clanTag, clanName;

    public BlizzardLegacyProfile(){}

    public BlizzardLegacyProfile(Long id, Integer realm, String name, String clanTag, String clanName)
    {
        super(id, realm, name);
        this.clanTag = clanTag;
        this.clanName = clanName;
    }

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

    public Instant getCreatedAt()
    {
        return createdAt;
    }

}
