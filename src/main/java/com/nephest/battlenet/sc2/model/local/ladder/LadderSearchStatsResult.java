// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.Race;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class LadderSearchStatsResult
{

    @NotNull
    private final Long playerCount;

    @NotNull
    private final Long teamCount;

    @NotNull
    private final Map<Race, Long> gamesPlayed;

    public LadderSearchStatsResult
    (
        Long playerCount,
        Long teamCount,
        Map<Race, Long> gamesPlayed
    )
    {
        this.playerCount = playerCount;
        this.teamCount = teamCount;
        this.gamesPlayed = gamesPlayed;
    }

    public Long getPlayerCount()
    {
        return playerCount;
    }

    public Long getTeamCount()
    {
        return teamCount;
    }

    public Map<Race, Long> getGamesPlayed()
    {
        return gamesPlayed;
    }

}
