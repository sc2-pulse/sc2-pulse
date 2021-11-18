// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class MapStats
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Integer id;

    @NotNull
    private Integer leagueId;
    
    private Integer mapId;

    @NotNull
    private Race race;

    @NotNull
    private Race versusRace;

    @NotNull
    private Integer gamesTotal;

    @NotNull
    private Integer gamesWithDuration;

    @NotNull
    private Integer wins;

    @NotNull
    private Integer ties;

    @NotNull
    private Integer losses;

    @NotNull
    private Integer duration;

    public MapStats(){}

    public MapStats
    (
        Integer id,
        Integer leagueId,
        Integer mapId,
        Race race,
        Race versusRace,
        Integer gamesTotal,
        Integer gamesWithDuration,
        Integer wins,
        Integer losses,
        Integer ties,
        Integer duration
    )
    {
        this.id = id;
        this.leagueId = leagueId;
        this.mapId = mapId;
        this.race = race;
        this.versusRace = versusRace;
        this.gamesTotal = gamesTotal;
        this.gamesWithDuration = gamesWithDuration;
        this.wins = wins;
        this.losses = losses;
        this.ties = ties;
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof MapStats)) return false;
        MapStats stats = (MapStats) o;
        return getLeagueId().equals(stats.getLeagueId())
            && Objects.equals(getMapId(), stats.getMapId())
            && getRace() == stats.getRace()
            && getVersusRace() == stats.getVersusRace();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLeagueId(), getMapId(), getRace(), getVersusRace());
    }

    @Override
    public String toString()
    {
        return "MapStats{"
            + "leagueId="
            + leagueId
            + ", mapId="
            + mapId
            + ", race="
            + race
            + ", versusRace="
            + versusRace
            + '}';
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getLeagueId()
    {
        return leagueId;
    }

    public void setLeagueId(Integer leagueId)
    {
        this.leagueId = leagueId;
    }

    public Integer getMapId()
    {
        return mapId;
    }

    public void setMapId(Integer mapId)
    {
        this.mapId = mapId;
    }

    public Race getRace()
    {
        return race;
    }

    public void setRace(Race race)
    {
        this.race = race;
    }

    public Race getVersusRace()
    {
        return versusRace;
    }

    public void setVersusRace(Race versusRace)
    {
        this.versusRace = versusRace;
    }

    public Integer getGamesTotal()
    {
        return gamesTotal;
    }

    public void setGamesTotal(Integer gamesTotal)
    {
        this.gamesTotal = gamesTotal;
    }

    public Integer getGamesWithDuration()
    {
        return gamesWithDuration;
    }

    public void setGamesWithDuration(Integer gamesWithDuration)
    {
        this.gamesWithDuration = gamesWithDuration;
    }

    public Integer getWins()
    {
        return wins;
    }

    public void setWins(Integer wins)
    {
        this.wins = wins;
    }

    public Integer getTies()
    {
        return ties;
    }

    public void setTies(Integer ties)
    {
        this.ties = ties;
    }

    public Integer getLosses()
    {
        return losses;
    }

    public void setLosses(Integer losses)
    {
        this.losses = losses;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

}
