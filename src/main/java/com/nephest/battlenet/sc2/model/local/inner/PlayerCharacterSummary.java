// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.BaseLeague;
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
    private Integer ratingLast;

    @NotNull
    private BaseLeague.LeagueType leagueTypeLast;

    private Integer globalRankLast;

    public PlayerCharacterSummary(){}

    public PlayerCharacterSummary
    (
        Long playerCharacterId,
        Race race,
        Integer games,
        Integer ratingAvg,
        Integer ratingMax,
        Integer ratingLast,
        BaseLeague.LeagueType leagueTypeLast,
        Integer globalRankLast
    )
    {
        this.playerCharacterId = playerCharacterId;
        this.race = race;
        this.games = games;
        this.ratingAvg = ratingAvg;
        this.ratingMax = ratingMax;
        this.ratingLast = ratingLast;
        this.leagueTypeLast = leagueTypeLast;
        this.globalRankLast = globalRankLast;
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

    public Integer getRatingLast()
    {
        return ratingLast;
    }

    public void setRatingLast(Integer ratingLast)
    {
        this.ratingLast = ratingLast;
    }

    public BaseLeague.LeagueType getLeagueTypeLast()
    {
        return leagueTypeLast;
    }

    public void setLeagueTypeLast(BaseLeague.LeagueType leagueTypeLast)
    {
        this.leagueTypeLast = leagueTypeLast;
    }

    public Integer getGlobalRankLast()
    {
        return globalRankLast;
    }

    public void setGlobalRankLast(Integer globalRankLast)
    {
        this.globalRankLast = globalRankLast;
    }

}
