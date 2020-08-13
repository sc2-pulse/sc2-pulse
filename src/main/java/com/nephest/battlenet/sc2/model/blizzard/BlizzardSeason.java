// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nephest.battlenet.sc2.model.BaseSeason;

import javax.validation.constraints.NotNull;

public class BlizzardSeason
extends BaseSeason
{

    @NotNull
    @JsonProperty("seasonId")
    private Integer id;

    public BlizzardSeason(){}

    public BlizzardSeason
    (
        Integer id,
        Integer year,
        Integer number
    )
    {
        super(year, number);
        this.id = id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getId()
    {
        return id;
    }

}
