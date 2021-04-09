// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.nephest.battlenet.sc2.model.BaseLeague;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class BlizzardProfileLadder
{

    public static final BlizzardProfileTeam[] EMPTY_LADDER_TEAMS = new BlizzardProfileTeam[0];

    @Valid
    @NotNull
    private BlizzardProfileTeam[] ladderTeams = EMPTY_LADDER_TEAMS;

    @NotNull @Valid
    private BaseLeague league;

    public BlizzardProfileLadder(){}

    public BlizzardProfileLadder
    (
        @Valid @NotNull BlizzardProfileTeam[] ladderTeams, @NotNull @Valid BaseLeague league
    )
    {
        this.ladderTeams = ladderTeams;
        this.league = league;
    }

    public BlizzardProfileTeam[] getLadderTeams()
    {
        return ladderTeams;
    }

    public void setLadderTeams(BlizzardProfileTeam[] ladderTeams)
    {
        this.ladderTeams = ladderTeams;
    }

    public BaseLeague getLeague()
    {
        return league;
    }

    public void setLeague(BaseLeague league)
    {
        this.league = league;
    }

}
