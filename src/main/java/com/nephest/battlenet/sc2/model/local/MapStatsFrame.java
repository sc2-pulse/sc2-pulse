// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.io.Serializable;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class MapStatsFrame
implements Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer mapStatsFilmId, number, wins, games;

    public MapStatsFrame()
    {
    }

    public MapStatsFrame(Integer number, Integer wins, Integer games)
    {
        this.number = number;
        this.wins = wins;
        this.games = games;
    }

    public MapStatsFrame(Integer mapStatsFilmId, Integer number, Integer wins, Integer games)
    {
        this.mapStatsFilmId = mapStatsFilmId;
        this.number = number;
        this.wins = wins;
        this.games = games;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof MapStatsFrame)) {return false;}
        MapStatsFrame that = (MapStatsFrame) o;
        return Objects.equals(getMapStatsFilmId(), that.getMapStatsFilmId())
            && Objects.equals(getNumber(), that.getNumber());
    }

    @Override
    public String toString()
    {
        return "MapStatsFrame{" + "mapStatsFilmId=" + mapStatsFilmId + ", number=" + number + '}';
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getMapStatsFilmId(), getNumber());
    }

    public Integer getMapStatsFilmId()
    {
        return mapStatsFilmId;
    }

    public void setMapStatsFilmId(Integer mapStatsFilmId)
    {
        this.mapStatsFilmId = mapStatsFilmId;
    }

    public Integer getNumber()
    {
        return number;
    }

    public void setNumber(Integer number)
    {
        this.number = number;
    }

    public Integer getWins()
    {
        return wins;
    }

    public void setWins(Integer wins)
    {
        this.wins = wins;
    }

    public Integer getGames()
    {
        return games;
    }

    public void setGames(Integer games)
    {
        this.games = games;
    }

}
