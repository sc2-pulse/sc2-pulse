// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

public class LadderPlayerSearchStats
{

    private final Integer rating;
    private final Integer gamesPlayed;
    private final Integer rank;

    public LadderPlayerSearchStats(Integer rating, Integer gamesPlayed, Integer rank)
    {
        this.rating = rating;
        this.gamesPlayed = gamesPlayed;
        this.rank = rank;
    }

    public Integer getRating()
    {
        return rating;
    }

    public Integer getGamesPlayed()
    {
        return gamesPlayed;
    }

    public Integer getRank()
    {
        return rank;
    }

}
