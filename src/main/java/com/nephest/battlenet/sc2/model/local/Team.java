// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Objects;

public class Team
extends BaseTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 8L;

    private Long id;

    @NotNull
    private BigInteger legacyId;

    @NotNull
    private Integer divisionId;

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
        BigInteger legacyId,
        Integer divisionId,
        BigInteger battlenetId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points
    )
    {
        super(rating, wins, losses, ties, points);
        this.id = id;
        this.legacyId = legacyId;
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
        BlizzardTeam bTeam,
        TeamDAO teamDAO
    )
    {
        return new Team
        (
            null,
            season.getBattlenetId(),
            season.getRegion(),
            league,
            tier.getType(),
            teamDAO.legacyIdOf(league, bTeam),
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
        return Objects.hash(getSeason(), getRegion(), getQueueType(), getLegacyId());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Team)) return false;
        Team team = (Team) o;
        return getSeason().equals(team.getSeason())
            && getRegion() == team.getRegion()
            && getQueueType() == team.getQueueType()
            && getLegacyId().equals(team.getLegacyId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s %s]",
            Team.class.getSimpleName(),
            getSeason(), getRegion().toString(), getQueueType(), getLegacyId()
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

    public BigInteger getLegacyId()
    {
        return legacyId;
    }

    public void setLegacyId(BigInteger legacyId)
    {
        this.legacyId = legacyId;
    }

    public void setDivisionId(Integer divisionId)
    {
        this.divisionId = divisionId;
    }

    public Integer getDivisionId()
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
