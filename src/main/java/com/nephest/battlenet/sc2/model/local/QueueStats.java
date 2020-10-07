// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class QueueStats
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    private Long id;

    @NotNull
    private Integer season;

    @NotNull
    private QueueType queueType;

    @NotNull
    private TeamType teamType;

    @NotNull
    private Long playerBase;

    @NotNull
    private Integer playerCount;

    @NotNull
    private Integer lowActivityPlayerCount = 0;

    @NotNull
    private Integer mediumActivityPlayerCount = 0;

    @NotNull
    private Integer highActivityPlayerCount = 0;

    public QueueStats
    (
        Long id,
        @NotNull Integer season,
        @NotNull QueueType queueType,
        @NotNull TeamType teamType,
        @NotNull Long playerBase,
        @NotNull Integer playerCount
    )
    {
        this.id = id;
        this.season = season;
        this.queueType = queueType;
        this.teamType = teamType;
        this.playerBase = playerBase;
        this.playerCount = playerCount;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueStats that = (QueueStats) o;
        return season.equals(that.season) && queueType == that.queueType && teamType == that.teamType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(season, queueType, teamType);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s]",
            getClass().getSimpleName(),
            getSeason(), getQueueType().toString(), getTeamType().toString()
        );
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Integer getSeason()
    {
        return season;
    }

    public void setSeason(Integer season)
    {
        this.season = season;
    }

    public QueueType getQueueType()
    {
        return queueType;
    }

    public void setQueueType(QueueType queueType)
    {
        this.queueType = queueType;
    }

    public TeamType getTeamType()
    {
        return teamType;
    }

    public void setTeamType(TeamType teamType)
    {
        this.teamType = teamType;
    }

    public Long getPlayerBase()
    {
        return playerBase;
    }

    public void setPlayerBase(Long playerBase)
    {
        this.playerBase = playerBase;
    }

    public Integer getPlayerCount()
    {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount)
    {
        this.playerCount = playerCount;
    }

    public Integer getLowActivityPlayerCount()
    {
        return lowActivityPlayerCount;
    }

    public void setLowActivityPlayerCount(Integer lowActivityPlayerCount)
    {
        this.lowActivityPlayerCount = lowActivityPlayerCount;
    }

    public Integer getMediumActivityPlayerCount()
    {
        return mediumActivityPlayerCount;
    }

    public void setMediumActivityPlayerCount(Integer mediumActivityPlayerCount)
    {
        this.mediumActivityPlayerCount = mediumActivityPlayerCount;
    }

    public Integer getHighActivityPlayerCount()
    {
        return highActivityPlayerCount;
    }

    public void setHighActivityPlayerCount(Integer highActivityPlayerCount)
    {
        this.highActivityPlayerCount = highActivityPlayerCount;
    }

}
