/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
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

    private static final long serialVersionUID = 1l;

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
        return getSeasonId() == otherLeague.getSeasonId()
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
            getClass().getSimpleName(),
            String.valueOf(getSeasonId()),
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
