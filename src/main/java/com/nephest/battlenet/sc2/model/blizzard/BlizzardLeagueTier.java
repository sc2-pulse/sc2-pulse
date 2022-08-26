// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import javax.validation.Valid;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardLeagueTier
extends BaseLeagueTier
{

    private static final BlizzardTierDivision[] EMPTY_DIVISION_ARRAY
        = new BlizzardTierDivision[0];

    //divisions can be empty
    @Valid
    @JsonProperty("division")
    private BlizzardTierDivision[] divisions = EMPTY_DIVISION_ARRAY;

    public BlizzardLeagueTier(){}

    public BlizzardLeagueTier(LeagueTierType type, int minRating, int maxRating)
    {
        super(type, minRating, maxRating);
    }

    @JsonProperty("id")
    @Override
    public void setType(LeagueTierType type)
    {
        super.setType(type);
    }

    @JsonProperty("id")
    @Override
    public LeagueTierType getType()
    {
        return super.getType();
    }

    public void setDivisions(BlizzardTierDivision[] divisions)
    {
        this.divisions = divisions;
    }

    public BlizzardTierDivision[] getDivisions()
    {
        return divisions;
    }

}
