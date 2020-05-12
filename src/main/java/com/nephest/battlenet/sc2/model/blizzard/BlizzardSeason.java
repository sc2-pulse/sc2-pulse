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
    private Long id;

    public BlizzardSeason(){}

    public BlizzardSeason
    (
        Long id,
        Integer year,
        Integer number
    )
    {
        super(year, number);
        this.id = id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

}
