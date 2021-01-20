// Copyright (C) 2020-2021 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.TeamState;

public class LadderTeamState
{

    private final TeamState teamState;
    private final Race race;
    private final League league;
    private final BaseLeagueTier.LeagueTierType tier;
    private final int season;

    public LadderTeamState
    (
        TeamState teamState, Race race, BaseLeagueTier.LeagueTierType tier, League league, int season
    )
    {
        this.teamState = teamState;
        this.race = race;
        this.league = league;
        this.tier = tier;
        this.season = season;
    }

    public TeamState getTeamState()
    {
        return teamState;
    }

    public Race getRace()
    {
        return race;
    }

    public League getLeague()
    {
        return league;
    }

    public BaseLeagueTier.LeagueTierType getTier()
    {
        return tier;
    }

    public int getSeason()
    {
        return season;
    }

}
