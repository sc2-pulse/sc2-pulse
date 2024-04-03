// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.io.Serializable;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class MapStatsFilm
implements Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer id, mapId, leagueTierId, mapStatsFilmSpecId;

    public MapStatsFilm()
    {
    }

    public MapStatsFilm(Integer mapId, Integer leagueTierId, Integer mapStatsFilmSpecId)
    {
        this.mapId = mapId;
        this.leagueTierId = leagueTierId;
        this.mapStatsFilmSpecId = mapStatsFilmSpecId;
    }

    public MapStatsFilm(Integer id, Integer mapId, Integer leagueTierId, Integer mapStatsFilmSpecId)
    {
        this.id = id;
        this.mapId = mapId;
        this.leagueTierId = leagueTierId;
        this.mapStatsFilmSpecId = mapStatsFilmSpecId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof MapStatsFilm)) {return false;}
        MapStatsFilm that = (MapStatsFilm) o;
        return Objects.equals(getMapId(), that.getMapId())
            && Objects.equals(getLeagueTierId(), that.getLeagueTierId())
            && Objects.equals(getMapStatsFilmSpecId(), that.getMapStatsFilmSpecId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getMapId(), getLeagueTierId(), getMapStatsFilmSpecId());
    }

    @Override
    public String toString()
    {
        return "MapStatsFilm{" + "mapId=" + mapId + ", leagueTierId=" + leagueTierId
            + ", mapStatsFilmSpecId=" + mapStatsFilmSpecId + '}';
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getMapId()
    {
        return mapId;
    }

    public void setMapId(Integer mapId)
    {
        this.mapId = mapId;
    }

    public Integer getLeagueTierId()
    {
        return leagueTierId;
    }

    public void setLeagueTierId(Integer leagueTierId)
    {
        this.leagueTierId = leagueTierId;
    }

    public Integer getMapStatsFilmSpecId()
    {
        return mapStatsFilmSpecId;
    }

    public void setMapStatsFilmSpecId(Integer mapStatsFilmSpecId)
    {
        this.mapStatsFilmSpecId = mapStatsFilmSpecId;
    }

}
