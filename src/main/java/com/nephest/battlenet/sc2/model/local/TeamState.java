// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class TeamState
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    @NotNull
    private Long teamId;

    @NotNull
    private OffsetDateTime dateTime = OffsetDateTime.now();

    @NotNull
    private Integer divisionId;

    @NotNull
    private Integer games;

    @NotNull
    private Integer rating;

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

    public static TeamState of(Team team)
    {
        return new TeamState
        (
            team.getId(),
            OffsetDateTime.now(),
            team.getDivisionId(),
            team.getWins() + team.getLosses() + team.getTies(),
            team.getRating().intValue()
        );
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

}
