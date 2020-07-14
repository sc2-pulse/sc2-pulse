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

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long season;

    @NotNull
    private QueueType queueType;

    @NotNull
    private TeamType teamType;

    @NotNull
    private Long playerBase;

    public QueueStats
    (
        Long id,
        @NotNull Long season,
        @NotNull QueueType queueType,
        @NotNull TeamType teamType,
        @NotNull Long playerBase
    )
    {
        this.id = id;
        this.season = season;
        this.queueType = queueType;
        this.teamType = teamType;
        this.playerBase = playerBase;
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

    public Long getSeason()
    {
        return season;
    }

    public void setSeason(Long season)
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
}
