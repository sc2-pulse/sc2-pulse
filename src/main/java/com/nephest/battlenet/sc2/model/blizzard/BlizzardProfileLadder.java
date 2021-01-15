// Copyright (C) 2021 Oleksandr Masniuk and contributors
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
    private BaseLeague.LeagueType leagueType;

    public BlizzardProfileLadder(){}

    public BlizzardProfileLadder
    (
        @Valid @NotNull BlizzardProfileTeam[] ladderTeams, @NotNull @Valid BaseLeague.LeagueType leagueType
    )
    {
        this.ladderTeams = ladderTeams;
        this.leagueType = leagueType;
    }

    public BlizzardProfileTeam[] getLadderTeams()
    {
        return ladderTeams;
    }

    public void setLadderTeams(BlizzardProfileTeam[] ladderTeams)
    {
        this.ladderTeams = ladderTeams;
    }

    public BaseLeague.LeagueType getLeagueType()
    {
        return leagueType;
    }

    public void setLeagueType(BaseLeague.LeagueType leagueType)
    {
        this.leagueType = leagueType;
    }

}
