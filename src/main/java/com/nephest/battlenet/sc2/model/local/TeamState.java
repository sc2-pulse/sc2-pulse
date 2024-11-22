// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class TeamState
implements java.io.Serializable
{

    private static final long serialVersionUID = 4L;

    public static final QueueType MAIN_QUEUE_TYPE = QueueType.LOTV_1V1;

    @NotNull
    private Long teamId;

    @NotNull
    private OffsetDateTime dateTime = SC2Pulse.offsetDateTime();

    @NotNull
    private Integer divisionId;

    private Integer wins;

    @NotNull
    private Integer games;

    @NotNull
    private Integer rating;

    private Integer globalRank;
    private Integer regionRank;
    private Integer leagueRank;

    private Boolean secondary;

    public TeamState(){}

    public TeamState
    (
        @NotNull Long teamId,
        @NotNull OffsetDateTime dateTime,
        @NotNull Integer divisionId,
        @NotNull Integer games,
        @NotNull Integer rating
    )
    {
        this.teamId = teamId;
        this.dateTime = dateTime;
        this.divisionId = divisionId;
        this.games = games;
        this.rating = rating;
    }

    public TeamState
    (
        @NotNull Long teamId,
        @NotNull OffsetDateTime dateTime,
        @NotNull Integer divisionId,
        Integer wins,
        @NotNull Integer games,
        @NotNull Integer rating,
        Integer globalRank,
        Integer regionRank,
        Integer leagueRank,
        Boolean secondary
    )
    {
        this(teamId, dateTime, divisionId, games, rating);
        this.wins = wins;
        this.globalRank = globalRank;
        this.regionRank = regionRank;
        this.leagueRank = leagueRank;
        this.secondary = secondary;
    }

    public static TeamState of(Team team, OffsetDateTime timestamp)
    {
        return new TeamState
        (
            team.getId(),
            timestamp,
            team.getDivisionId(),
            team.getWins(),
            team.getWins() + team.getLosses() + team.getTies(),
            team.getRating().intValue(),
            null, null, null,
            team.getQueueType() != MAIN_QUEUE_TYPE ? true : null
        );
    }

    public static TeamState of(Team team)
    {
        return TeamState.of(team, SC2Pulse.offsetDateTime());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamState that = (TeamState) o;
        return teamId.equals(that.teamId) && dateTime.isEqual(that.dateTime);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(teamId, dateTime.toEpochSecond());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            TeamState.class.getSimpleName(),
            teamId, dateTime
        );
    }

    public Long getTeamId()
    {
        return teamId;
    }

    public void setTeamId(Long teamId)
    {
        this.teamId = teamId;
    }

    public OffsetDateTime getDateTime()
    {
        return dateTime;
    }

    public void setDateTime(OffsetDateTime dateTime)
    {
        this.dateTime = dateTime;
    }

    public Integer getDivisionId()
    {
        return divisionId;
    }

    public void setDivisionId(Integer divisionId)
    {
        this.divisionId = divisionId;
    }

    public Integer getWins()
    {
        return wins;
    }

    public void setWins(Integer wins)
    {
        this.wins = wins;
    }

    public Integer getGames()
    {
        return games;
    }

    public void setGames(Integer games)
    {
        this.games = games;
    }

    public Integer getRating()
    {
        return rating;
    }

    public void setRating(Integer rating)
    {
        this.rating = rating;
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

    public Boolean getSecondary()
    {
        return secondary;
    }

    public void setSecondary(Boolean secondary)
    {
        this.secondary = secondary;
    }

}
