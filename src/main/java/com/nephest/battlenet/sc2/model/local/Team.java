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

    private static final long serialVersionUID = 4L;

    private Long id;

    @NotNull
    private Long divisionId;

    private BigInteger battlenetId;

    @NotNull
    private Integer season;

    @NotNull
    private Region region;

    @NotNull
    private BaseLeague league;

    @NotNull
    private LeagueTier.LeagueTierType tierType;

    @NotNull
    private Integer globalRank = 2147483647;

    @NotNull
    private Integer regionRank = 2147483647;

    @NotNull
    private Integer leagueRank = 2147483647;

    public Team(){}

    public Team
    (
        Long id,
        Integer season, Region region,
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

    public static Team of
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
        return Objects.hash(getBattlenetId(), getSeason(), getRegion());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(getBattlenetId(), team.getBattlenetId())
            && getSeason().equals(team.getSeason())
            && getRegion() == team.getRegion();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s]",
            Team.class.getSimpleName(),
            getSeason(), getRegion().toString(), getBattlenetId()
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

    public void setSeason(Integer season)
    {
        this.season = season;
    }

    public Integer getSeason()
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

    public BaseLeague getLeague()
    {
        return league;
    }

    public void setLeague(BaseLeague league)
    {
        this.league = league;
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

    public Integer getGlobalRank()
    {
        return globalRank;
    }

    public void setGlobalRank(Integer globalRank)
    {
        this.globalRank = globalRank;
    }

    public Integer getRegionRank()
    {
        return regionRank;
    }

    public void setRegionRank(Integer regionRank)
    {
        this.regionRank = regionRank;
    }

    public Integer getLeagueRank()
    {
        return leagueRank;
    }

    public void setLeagueRank(Integer leagueRank)
    {
        this.leagueRank = leagueRank;
    }

}
