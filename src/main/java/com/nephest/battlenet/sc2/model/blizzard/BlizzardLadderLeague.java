// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
