// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;

import javax.validation.constraints.NotNull;

public class LadderPlayerCharacterStats
{

    @NotNull
    private final PlayerCharacterStats stats;

    private final Integer ratingCurrent;
    private final Integer gamesPlayedCurrent;

    public LadderPlayerCharacterStats(PlayerCharacterStats stats, Integer ratingCurrent, Integer gamesPlayedCurrent)
    {
        this.stats = stats;
        this.ratingCurrent = ratingCurrent;
        this.gamesPlayedCurrent = gamesPlayedCurrent;
    }

    public PlayerCharacterStats getStats()
    {
        return stats;
    }

    public Integer getRatingCurrent()
    {
        return ratingCurrent;
    }

    public Integer getGamesPlayedCurrent()
    {
        return gamesPlayedCurrent;
    }

}
