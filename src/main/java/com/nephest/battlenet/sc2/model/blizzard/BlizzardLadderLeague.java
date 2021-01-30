// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BlizzardLadderLeague
{

    @NotNull @Valid
    private BlizzardLadderLeagueKey leagueKey;

    public BlizzardLadderLeagueKey getLeagueKey()
    {
        return leagueKey;
    }

    public void setLeagueKey(BlizzardLadderLeagueKey leagueKey)
    {
        this.leagueKey = leagueKey;
    }

}
