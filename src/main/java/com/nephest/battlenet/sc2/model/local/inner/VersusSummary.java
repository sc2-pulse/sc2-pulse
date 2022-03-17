// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

public class VersusSummary
{

    private Integer matches;

    private Integer wins;

    private Integer losses;

    public VersusSummary(){}

    public VersusSummary(Integer matches, Integer wins, Integer losses)
    {
        this.matches = matches;
        this.wins = wins;
        this.losses = losses;
    }

    public Integer getMatches()
    {
        return matches;
    }

    public Integer getWins()
    {
        return wins;
    }

    public Integer getLosses()
    {
        return losses;
    }

}
