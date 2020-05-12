// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.Valid;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardLadder
{

    private static final BlizzardTeam[] EMPTY_TEAM_ARRAY = new BlizzardTeam[0];

    //ladder can be empty
    @Valid
    @JsonProperty("team")
    private BlizzardTeam[] teams = EMPTY_TEAM_ARRAY;

    public BlizzardLadder(){}

    public BlizzardLadder(BlizzardTeam[] teams)
    {
        this.teams = teams;
    }

    public void setTeams(BlizzardTeam[] teams)
    {
        this.teams = teams;
    }

    public BlizzardTeam[] getTeams()
    {
        return teams;
    }

}

