// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class League
extends BaseLeague
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long seasonId;

    public League(){}

    public League
    (
        Long id, Long seasonId,
        LeagueType type, QueueType queueType, TeamType teamType
    )
    {
        super(type, queueType, teamType);
        this.id = id;
        this.seasonId = seasonId;
    }

    public static League of(Season season, BlizzardLeague league)
    {
        return new League
        (
            null,
            season.getId(),
            league.getType(),
            league.getQueueType(),
            league.getTeamType()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSeasonId(), getType(), getQueueType(), getTeamType());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof League) ) return false;

        League otherLeague = (League) other;
        return getSeasonId().equals(otherLeague.getSeasonId())
            && getType() == otherLeague.getType()
            && getQueueType() == otherLeague.getQueueType()
            && getTeamType() == otherLeague.getTeamType();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s %s]",
            League.class.getSimpleName(),
            getSeasonId(),
            getType().toString(),
            getQueueType().toString(),
            getTeamType().toString()
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

    public void setSeasonId(Long seasonId)
    {
        this.seasonId = seasonId;
    }

    public Long getSeasonId()
    {
        return seasonId;
    }

}
