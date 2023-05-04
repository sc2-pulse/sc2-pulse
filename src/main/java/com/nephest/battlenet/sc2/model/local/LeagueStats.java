// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.util.Objects;
import javax.validation.constraints.NotNull;

public class LeagueStats
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    @NotNull
    private Integer leagueId;

    @NotNull
    private Integer teamCount;

    private Integer terranTeamCount, protossTeamCount, zergTeamCount, randomTeamCount;

    public LeagueStats
    (
        Integer leagueId,
        Integer teamCount,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed,
        Integer terranTeamCount,
        Integer protossTeamCount,
        Integer zergTeamCount,
        Integer randomTeamCount
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.leagueId = leagueId;
        this.teamCount = teamCount;
        this.terranTeamCount = terranTeamCount;
        this.protossTeamCount = protossTeamCount;
        this.zergTeamCount = zergTeamCount;
        this.randomTeamCount = randomTeamCount;
    }

    public LeagueStats
    (
        Integer leagueId,
        Integer teamCount,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        this
        (
            leagueId,
            teamCount,
            terranGamesPlayed,
            protossGamesPlayed,
            zergGamesPlayed,
            randomGamesPlayed,
            null,
            null,
            null,
            null
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLeagueId());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof LeagueStats) ) return false;

        LeagueStats otherStats = (LeagueStats) other;
        return getLeagueId() != null
            && getLeagueId().equals(otherStats.getLeagueId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            LeagueStats.class.getSimpleName(),
            getLeagueId()
        );
    }

    public void setLeagueId(Integer leagueId)
    {
        this.leagueId = leagueId;
    }

    public Integer getLeagueId()
    {
        return leagueId;
    }

    public void setTeamCount(Integer teamCount)
    {
        this.teamCount = teamCount;
    }

    public Integer getTeamCount()
    {
        return teamCount;
    }

    public Integer getTerranTeamCount()
    {
        return terranTeamCount;
    }

    public void setTerranTeamCount(Integer terranTeamCount)
    {
        this.terranTeamCount = terranTeamCount;
    }

    public Integer getProtossTeamCount()
    {
        return protossTeamCount;
    }

    public void setProtossTeamCount(Integer protossTeamCount)
    {
        this.protossTeamCount = protossTeamCount;
    }

    public Integer getZergTeamCount()
    {
        return zergTeamCount;
    }

    public void setZergTeamCount(Integer zergTeamCount)
    {
        this.zergTeamCount = zergTeamCount;
    }

    public Integer getRandomTeamCount()
    {
        return randomTeamCount;
    }

    public void setRandomTeamCount(Integer randomTeamCount)
    {
        this.randomTeamCount = randomTeamCount;
    }
    
}
