// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class SeasonState
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer seasonId;

    @NotNull
    private OffsetDateTime periodStart;

    @NotNull
    private Integer playerCount;

    @NotNull
    private Integer totalGamesPlayed;

    private Integer gamesPlayed;

    public SeasonState(){}

    public SeasonState
    (
        @NotNull Integer seasonId,
        @NotNull OffsetDateTime periodStart,
        @NotNull Integer playerCount,
        @NotNull Integer totalGamesPlayed,
        Integer gamesPlayed
    )
    {
        this.seasonId = seasonId;
        this.periodStart = periodStart;
        this.playerCount = playerCount;
        this.totalGamesPlayed = totalGamesPlayed;
        this.gamesPlayed = gamesPlayed;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SeasonState that)) return false;
        return seasonId.equals(that.seasonId) && periodStart.equals(that.periodStart);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(seasonId, periodStart);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            SeasonState.class.getSimpleName(),
            getSeasonId(), getPeriodStart()
        );
    }

    public Integer getSeasonId()
    {
        return seasonId;
    }

    public void setSeasonId(Integer seasonId)
    {
        this.seasonId = seasonId;
    }

    public OffsetDateTime getPeriodStart()
    {
        return periodStart;
    }

    public void setPeriodStart(OffsetDateTime periodStart)
    {
        this.periodStart = periodStart;
    }

    public Integer getPlayerCount()
    {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount)
    {
        this.playerCount = playerCount;
    }

    public Integer getTotalGamesPlayed()
    {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(Integer totalGamesPlayed)
    {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public Integer getGamesPlayed()
    {
        return gamesPlayed;
    }

    public void setGamesPlayed(Integer gamesPlayed)
    {
        this.gamesPlayed = gamesPlayed;
    }

}
