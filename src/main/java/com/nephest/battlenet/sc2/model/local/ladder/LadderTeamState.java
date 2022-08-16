// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.TeamState;

public class LadderTeamState
{

    private TeamState teamState;
    private Race race;
    private League league;
    private BaseLeagueTier.LeagueTierType tier;
    private int season;

    @JsonUnwrapped
    private PopulationState populationState;

    public LadderTeamState()
    {
    }

    public LadderTeamState
    (
        TeamState teamState,
        Race race,
        BaseLeagueTier.LeagueTierType tier,
        League league,
        int season,
        PopulationState populationState
    )
    {
        this.teamState = teamState;
        this.race = race;
        this.league = league;
        this.tier = tier;
        this.season = season;
        this.populationState = populationState;
    }

    public TeamState getTeamState()
    {
        return teamState;
    }

    public void setTeamState(TeamState teamState)
    {
        this.teamState = teamState;
    }

    public Race getRace()
    {
        return race;
    }

    public void setRace(Race race)
    {
        this.race = race;
    }

    public League getLeague()
    {
        return league;
    }

    public void setLeague(League league)
    {
        this.league = league;
    }

    public BaseLeagueTier.LeagueTierType getTier()
    {
        return tier;
    }

    public void setTier(BaseLeagueTier.LeagueTierType tier)
    {
        this.tier = tier;
    }

    public int getSeason()
    {
        return season;
    }

    public void setSeason(int season)
    {
        this.season = season;
    }

    public PopulationState getPopulationState()
    {
        return populationState;
    }

    public void setPopulationState(PopulationState populationState)
    {
        this.populationState = populationState;
    }

}
