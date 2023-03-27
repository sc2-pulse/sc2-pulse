// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.arcade;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.StringToUnsignedLongConverter;
import com.nephest.battlenet.sc2.config.convert.jackson.UnsignedLongSerializer;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;

public class ArcadePlayerCharacter
extends BlizzardFullPlayerCharacter
{

    @JsonDeserialize(converter = StringToUnsignedLongConverter.class)
    @JsonSerialize(using = UnsignedLongSerializer.class)
    private Long profileGameId;

    public ArcadePlayerCharacter()
    {
    }

    public ArcadePlayerCharacter(Long id, Integer realm, String name, Long profileGameId)
    {
        super(id, realm, name);
        this.profileGameId = profileGameId;
    }

    public ArcadePlayerCharacter
    (
        Long id,
        Integer realm,
        String name,
        Region region,
        Long profileGameId
    )
    {
        super(id, realm, name, region);
        this.profileGameId = profileGameId;
    }

    public Long getProfileGameId()
    {
        return profileGameId;
    }

    public void setProfileGameId(Long profileGameId)
    {
        this.profileGameId = profileGameId;
    }

}
