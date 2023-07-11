// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseTeam;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class Team
extends BaseTeam
implements java.io.Serializable
{

    public static final Comparator<Team> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Team::getQueueType)
            .thenComparing(Team::getRegion)
            .thenComparing(Team::getLegacyId)
            .thenComparing(Team::getSeason);

    private static final long serialVersionUID = 9L;

    private Long id;

    @NotNull
    private BigInteger legacyId;

    @NotNull
    private Integer divisionId;

    @NotNull
    private Integer season;

    @NotNull
    private Region region;

    @NotNull
    private BaseLeague league;

    private LeagueTier.LeagueTierType tierType;

    private Integer globalRank;

    private Integer regionRank;

    private Integer leagueRank;

    private OffsetDateTime lastPlayed;

    public Team(){}

    public Team
    (
        Long id,
        Integer season, Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType tierType,
        BigInteger legacyId,
        Integer divisionId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points,
        OffsetDateTime lastPlayed
    )
    {
        super(rating, wins, losses, ties, points);
        this.id = id;
        this.legacyId = legacyId;
        this.divisionId = divisionId;
        this.season = season;
        this.region = region;
        this.league = league;
        this.tierType = tierType;
        this.lastPlayed = lastPlayed;
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
            bTeam.getRating(),
            bTeam.getWins(), bTeam.getLosses(), bTeam.getTies(),
            bTeam.getPoints(),
            bTeam.getLastPlayedTimeStamp() != null
                ? bTeam.getLastPlayedTimeStamp().atOffset(OffsetDateTime.now().getOffset())
                : OffsetDateTime.now()
        );
    }

    public static Team uid
    (
        QueueType queueType,
        Region region,
        BigInteger legacyId,
        Integer season
    )
    {
        return new Team
        (
            null,
            season,
            region,
            new BaseLeague
            (
                null,
                queueType,
                null
            ),
            null,
            legacyId,
            null,
            null,
            null, null, null,
            null,
            null
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

    public OffsetDateTime getLastPlayed()
    {
        return lastPlayed;
    }

    public void setLastPlayed(OffsetDateTime lastPlayed)
    {
        this.lastPlayed = lastPlayed;
    }

}
