// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

public class MapStatsFilm
implements Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer id, mapId, leagueTierId, mapStatsFilmSpecId;

    @NotNull
    private Boolean crossTier;

    public MapStatsFilm()
    {
    }

    public MapStatsFilm
    (
        Integer mapId,
        Integer leagueTierId,
        Integer mapStatsFilmSpecId,
        @NotNull Boolean crossTier
    )
    {
        this.mapId = mapId;
        this.leagueTierId = leagueTierId;
        this.mapStatsFilmSpecId = mapStatsFilmSpecId;
        this.crossTier = crossTier;
    }

    public MapStatsFilm
    (
        Integer id,
        Integer mapId,
        Integer leagueTierId,
        Integer mapStatsFilmSpecId,
        @NotNull Boolean crossTier
    )
    {
        this(mapId, leagueTierId, mapStatsFilmSpecId, crossTier);
        this.id = id;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof MapStatsFilm that)) {return false;}
        return Objects.equals(getMapId(), that.getMapId())
            && Objects.equals(getLeagueTierId(), that.getLeagueTierId())
            && Objects.equals(getMapStatsFilmSpecId(), that.getMapStatsFilmSpecId())
            && Objects.equals(isCrossTier(), that.isCrossTier());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getMapId(), getLeagueTierId(), getMapStatsFilmSpecId(), isCrossTier());
    }

    @Override
    public String toString()
    {
        return "MapStatsFilm{" + "mapId=" + mapId + ", leagueTierId=" + leagueTierId
            + ", mapStatsFilmSpecId=" + mapStatsFilmSpecId + ", crossTier=" + crossTier + '}';
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

    public Boolean isCrossTier()
    {
        return crossTier;
    }

    public void setCrossTier(@NotNull Boolean crossTier)
    {
        this.crossTier = crossTier;
    }

}
