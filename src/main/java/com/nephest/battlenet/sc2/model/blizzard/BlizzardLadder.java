// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.util.TimestampedObject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardLadder
extends TimestampedObject
{

    private static final BlizzardTeam[] EMPTY_TEAM_ARRAY = new BlizzardTeam[0];

    //ladder can be empty
    @Valid
    @JsonProperty("team")
    private BlizzardTeam[] teams = EMPTY_TEAM_ARRAY;

    @NotNull @Valid
    private BlizzardLadderLeague league;

    public BlizzardLadder(){}

    public BlizzardLadder(@Valid BlizzardTeam[] teams, @NotNull @Valid BlizzardLadderLeague league)
    {
        this.teams = teams;
        this.league = league;
    }

    public void setTeams(BlizzardTeam[] teams)
    {
        this.teams = teams;
    }

    public BlizzardTeam[] getTeams()
    {
        return teams;
    }

    public BlizzardLadderLeague getLeague()
    {
        return league;
    }

    public void setLeague(BlizzardLadderLeague league)
    {
        this.league = league;
    }

}

