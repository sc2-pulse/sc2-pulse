// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueStats;
import com.nephest.battlenet.sc2.model.local.Season;
import jakarta.validation.constraints.NotNull;

public class LadderLeagueStats
{

    @NotNull
    private final Season season;

    @NotNull
    private final League league;

    @NotNull
    private final LeagueStats leagueStats;

    public LadderLeagueStats(Season season, League league, LeagueStats leagueStats)
    {
        this.season = season;
        this.league = league;
        this.leagueStats = leagueStats;
    }

    public Season getSeason()
    {
        return season;
    }

    public League getLeague()
    {
        return league;
    }

    public LeagueStats getLeagueStats()
    {
        return leagueStats;
    }

}
