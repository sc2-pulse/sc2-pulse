// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardTierDivision
{

    @NotNull
    private Long ladderId;

    public BlizzardTierDivision(){}

    public BlizzardTierDivision(Long ladderId)
    {
        this.ladderId = ladderId;
    }

    public void setLadderId(Long ladderId)
    {
        this.ladderId = ladderId;
    }

    public Long getLadderId()
    {
        return ladderId;
    }

}
