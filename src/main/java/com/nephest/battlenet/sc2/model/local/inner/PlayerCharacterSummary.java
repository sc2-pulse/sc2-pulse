// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.Race;

import javax.validation.constraints.NotNull;

public class PlayerCharacterSummary
{

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private Race race;

    @NotNull
    private Integer games;

    @NotNull
    private Integer ratingAvg;

    @NotNull
    private Integer ratingMax;

    @NotNull
    private Integer ratingCur;

    public PlayerCharacterSummary(){}

    public PlayerCharacterSummary
    (Long playerCharacterId, Race race, Integer games, Integer ratingAvg, Integer ratingMax, Integer ratingCur)
    {
        this.playerCharacterId = playerCharacterId;
        this.race = race;
        this.games = games;
        this.ratingAvg = ratingAvg;
        this.ratingMax = ratingMax;
        this.ratingCur = ratingCur;
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public Race getRace()
    {
        return race;
    }

    public void setRace(Race race)
    {
        this.race = race;
    }

    public Integer getGames()
    {
        return games;
    }

    public void setGames(Integer games)
    {
        this.games = games;
    }

    public Integer getRatingAvg()
    {
        return ratingAvg;
    }

    public void setRatingAvg(Integer ratingAvg)
    {
        this.ratingAvg = ratingAvg;
    }

    public Integer getRatingMax()
    {
        return ratingMax;
    }

    public void setRatingMax(Integer ratingMax)
    {
        this.ratingMax = ratingMax;
    }

    public Integer getRatingCur()
    {
        return ratingCur;
    }

    public void setRatingCur(Integer ratingCur)
    {
        this.ratingCur = ratingCur;
    }

}
