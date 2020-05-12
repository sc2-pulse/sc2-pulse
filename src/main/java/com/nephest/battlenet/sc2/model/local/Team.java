// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Objects;

public class Team
extends BaseTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private Long id;

    @NotNull
    private Long divisionId;

    @NotNull
    private BigInteger battlenetId;

    @NotNull
    private Long season;

    @NotNull
    private Region region;

    @NotNull
    private BaseLeague league;

    @NotNull
    private LeagueTier.LeagueTierType tierType;

    public Team(){}

    public Team
    (
        Long id,
        Long season, Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType tierType,
        Long divisionId,
        BigInteger battlenetId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points
    )
    {
        super(rating, wins, losses, ties, points);
        this.id = id;
        this.divisionId = divisionId;
        this.battlenetId = battlenetId;
        this.season = season;
        this.region = region;
        this.league = league;
        this.tierType = tierType;
    }

    public static final Team of
    (
        Season season,
        League league,
        LeagueTier tier,
        Division division,
        BlizzardTeam bTeam
    )
    {
        return new Team
        (
            null,
            season.getBattlenetId(),
            season.getRegion(),
            league,
            tier.getType(),
            division.getId(),
            bTeam.getId(),
            bTeam.getRating(),
            bTeam.getWins(), bTeam.getLosses(), bTeam.getTies(),
            bTeam.getPoints()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSeason(), getRegion(), getBattlenetId());
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if( !(other instanceof Team) ) return false;

        Team otherTeam = (Team) other;
        return getSeason().equals(otherTeam.getSeason())
            && getRegion() == otherTeam.getRegion()
            && getBattlenetId().equals(otherTeam.getBattlenetId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s]",
            getClass().getSimpleName(),
            String.valueOf(getSeason()), getRegion().toString(), String.valueOf(getBattlenetId())
        );
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setDivisionId(Long divisionId)
    {
        this.divisionId = divisionId;
    }

    public Long getDivisionId()
    {
        return divisionId;
    }

    public void setBattlenetId(BigInteger battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public BigInteger getBattlenetId()
    {
        return battlenetId;
    }

    public void setSeason(Long season)
    {
        this.season = season;
    }

    public Long getSeason()
    {
        return season;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setLeagueType(League.LeagueType type)
    {
        league.setType(type);
    }

    public League.LeagueType getLeagueType()
    {
        return league.getType();
    }

    public void setQueueType(QueueType type)
    {
        league.setQueueType(type);
    }

    public QueueType getQueueType()
    {
        return league.getQueueType();
    }

    public void setTeamType(TeamType type)
    {
        league.setTeamType(type);
    }

    public TeamType getTeamType()
    {
        return league.getTeamType();
    }

    public void setTierType(LeagueTier.LeagueTierType tierType)
    {
        this.tierType = tierType;
    }

    public LeagueTier.LeagueTierType getTierType()
    {
        return tierType;
    }

}
