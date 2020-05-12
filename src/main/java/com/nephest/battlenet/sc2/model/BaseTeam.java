// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.NotNull;

public class BaseTeam
{

    @NotNull
    private Long rating;

    @NotNull
    private Integer wins;

    @NotNull
    private Integer losses;

    @NotNull
    private Integer ties;

    @NotNull
    private Integer points;

    public BaseTeam(){}

    public BaseTeam(Long rating, Integer wins, Integer losses, Integer ties, Integer points)
    {
        setRating(rating);
        this.wins = wins;
        this.losses = losses;
        this.ties = ties;
        this.points = points;
    }

    public void setRating(Long rating)
    {
        this.rating = rating;
    }

    public Long getRating()
    {
        return rating;
    }

    public void setWins(Integer wins)
    {
        this.wins = wins;
    }

    public Integer getWins()
    {
        return wins;
    }

    public void setLosses(Integer losses)
    {
        this.losses = losses;
    }

    public Integer getLosses()
    {
        return losses;
    }

    public void setTies(Integer ties)
    {
        this.ties = ties;
    }

    public Integer getTies()
    {
        return ties;
    }

    public void setPoints(Integer points)
    {
        this.points = points;
    }

    public Integer getPoints()
    {
        return points;
    }

}
