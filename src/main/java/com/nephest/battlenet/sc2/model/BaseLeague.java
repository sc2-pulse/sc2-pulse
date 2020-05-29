// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonValue;

import javax.validation.constraints.NotNull;

public class BaseLeague
{

    public enum LeagueType
    implements Identifiable
    {

        BRONZE(0), SILVER(1), GOLD(2), PLATINUM(3), DIAMOND(4), MASTER(5), GRANDMASTER(6);

        private final int id;

        LeagueType(int id)
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

