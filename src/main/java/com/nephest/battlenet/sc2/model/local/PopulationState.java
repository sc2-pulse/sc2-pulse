// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.util.Objects;
import javax.validation.constraints.NotNull;

public class PopulationState
{

    private Integer id;

    @NotNull
    private Integer leagueId;

    @NotNull
    private Integer globalTeamCount;

    @NotNull
    private Integer regionTeamCount;

    private Integer leagueTeamCount;

    private Integer regionLeagueTeamCount;

    public PopulationState(){}

    public PopulationState
    (
        Integer leagueId,
        Integer globalTeamCount,
        Integer regionTeamCount
    )
    {
        this.leagueId = leagueId;
        this.globalTeamCount = globalTeamCount;
        this.regionTeamCount = regionTeamCount;
    }

    public PopulationState
    (
        Integer id,
        Integer leagueId,
        Integer globalTeamCount,
        Integer regionTeamCount,
        Integer leagueTeamCount,
        Integer regionLeagueTeamCount
    )
    {
        this.id = id;
        this.globalTeamCount = globalTeamCount;
        this.regionTeamCount = regionTeamCount;
        this.leagueTeamCount = leagueTeamCount;
        this.regionLeagueTeamCount = regionLeagueTeamCount;
        this.leagueId = leagueId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof PopulationState)) {return false;}
        PopulationState that = (PopulationState) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getId());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s]", PopulationState.class.getSimpleName(), getId());
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getGlobalTeamCount()
    {
        return globalTeamCount;
    }

    public void setGlobalTeamCount(Integer globalTeamCount)
    {
        this.globalTeamCount = globalTeamCount;
    }

    public Integer getRegionTeamCount()
    {
        return regionTeamCount;
    }

    public void setRegionTeamCount(Integer regionTeamCount)
    {
        this.regionTeamCount = regionTeamCount;
    }

    public Integer getLeagueTeamCount()
    {
        return leagueTeamCount;
    }

    public void setLeagueTeamCount(Integer leagueTeamCount)
    {
        this.leagueTeamCount = leagueTeamCount;
    }

    public Integer getRegionLeagueTeamCount()
    {
        return regionLeagueTeamCount;
    }

    public void setRegionLeagueTeamCount(Integer regionLeagueTeamCount)
    {
        this.regionLeagueTeamCount = regionLeagueTeamCount;
    }

    public Integer getLeagueId()
    {
        return leagueId;
    }

    public void setLeagueId(Integer leagueId)
    {
        this.leagueId = leagueId;
    }

}
