// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.aligulac;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseProTeam;

import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AligulacProTeam
extends BaseProTeam
{

    @NotNull
    private Long id;

    public AligulacProTeam(){super();}

    public AligulacProTeam(@NotNull Long id, @NotNull String name, String shortName)
    {
        super(name, shortName);
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @Override
    @JsonProperty("shortname")
    public String getShortName()
    {
        return super.getShortName();
    }

}
