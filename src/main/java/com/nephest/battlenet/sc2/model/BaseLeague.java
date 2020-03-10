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
package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.*;

public class BaseLeague
{

    public static enum LeagueType
    implements Identifiable
    {

        BRONZE(0), SILVER(1), GOLD(2), PLATINUM(3), DIAMOND(4), MASTER(5), GRANDMASTER(6);

        private final int id;

        private LeagueType(int id)
        {
            this.id = id;
        }

        public static LeagueType from(int id)
        {
            for (LeagueType type : LeagueType.values())
            {
                if (type.getId() == id) return type;
            }
            throw new IllegalArgumentException("Invalid id");
        }

        @Override
        @JsonValue
        public int getId()
        {
            return id;
        }

    }

    @NotNull
    private LeagueType type;

    @NotNull
    private QueueType queueType;

    @NotNull
    private TeamType teamType;

    public BaseLeague(){}

    public BaseLeague
    (
        LeagueType type, QueueType queueType, TeamType teamType
    )
    {
        this.type = type;
        this.queueType = queueType;
        this.teamType = teamType;
    }

    public void setType(LeagueType type)
    {
        this.type = type;
    }

    public LeagueType getType()
    {
        return type;
    }

    public void setQueueType(QueueType queueType)
    {
        this.queueType = queueType;
    }

    public QueueType getQueueType()
    {
        return queueType;
    }

    public void setTeamType(TeamType teamType)
    {
        this.teamType = teamType;
    }

    public TeamType getTeamType()
    {
        return teamType;
    }

}

