// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.TeamLegacyUidToStringConverter;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseTeam;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.DelegatedTeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;

@JsonIgnoreProperties(value={"legacyUid"}, allowGetters=true)
public class Team
extends BaseTeam
implements java.io.Serializable
{

    public static final Comparator<Team> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Team::getQueueType)
            .thenComparing(Team::getTeamType)
            .thenComparing(Team::getRegion)
            .thenComparing(Team::getLegacyId)
            .thenComparing(Team::getSeason);

    private static final long serialVersionUID = 11L;

    private Long id;

    @NotNull
    private String legacyId;

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

    private transient TeamLegacyUid legacyUid = new DelegatedTeamLegacyUid(this);

    public Team()
    {
        this.league = new BaseLeague();
    }

    public Team
    (
        Long id,
        Integer season, Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType tierType,
        String legacyId,
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
                ? bTeam.getLastPlayedTimeStamp().atOffset(SC2Pulse.offsetDateTime().getOffset())
                : SC2Pulse.offsetDateTime()
        );
    }

    public static Team uid
    (
        QueueType queueType,
        TeamType teamType,
        Region region,
        String legacyId,
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
                teamType
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
        return Objects.hash
        (
            getSeason(),
            getRegion(),
            getQueueType(),
            getTeamType(),
            getLegacyId()
        );
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Team team)) return false;
        return getSeason().equals(team.getSeason())
            && getRegion() == team.getRegion()
            && getQueueType() == team.getQueueType()
            && getTeamType() == team.getTeamType()
            && getLegacyId().equals(team.getLegacyId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s %s %s]",
            Team.class.getSimpleName(),
            getSeason(), getRegion().toString(), getQueueType(), getTeamType(), getLegacyId()
        );
    }

    @Serial
    private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        legacyUid = new DelegatedTeamLegacyUid(this);
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public String getLegacyId()
    {
        return legacyId;
    }

    public void setLegacyId(String legacyId)
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

    @JsonSerialize(converter = TeamLegacyUidToStringConverter.class)
    public TeamLegacyUid getLegacyUid()
    {
        return legacyUid;
    }

}
