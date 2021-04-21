// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class SeasonState
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer seasonId;

    @NotNull
    private OffsetDateTime timestamp;

    @NotNull
    private Integer playerCount;

    @NotNull
    private Integer totalGamesPlayed;

    private Integer gamesPlayed;

    public SeasonState(){}

    public SeasonState
    (
        @NotNull Integer seasonId,
        @NotNull OffsetDateTime timestamp,
        @NotNull Integer playerCount,
        @NotNull Integer totalGamesPlayed,
        Integer gamesPlayed
    )
    {
        this.seasonId = seasonId;
        this.timestamp = timestamp;
        this.playerCount = playerCount;
        this.totalGamesPlayed = totalGamesPlayed;
        this.gamesPlayed = gamesPlayed;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SeasonState)) return false;
        SeasonState that = (SeasonState) o;
        return seasonId.equals(that.seasonId) && timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(seasonId, timestamp);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            SeasonState.class.getSimpleName(),
            getSeasonId(), getTimestamp()
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

    public OffsetDateTime getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp)
    {
        this.timestamp = timestamp;
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
