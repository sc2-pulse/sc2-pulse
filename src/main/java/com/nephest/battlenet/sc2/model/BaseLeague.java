// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import javax.validation.constraints.NotNull;

public class BaseLeague
{

    public enum LeagueType
    implements Identifiable
    {

        BRONZE(0, "bronze"),
        SILVER(1, "silver"),
        GOLD(2, "gold"),
        PLATINUM(3, "platinum"),
        DIAMOND(4, "diamond"),
        MASTER(5, "master"),
        GRANDMASTER(6, "grandmaster");

        private final int id;
        private final String name;

        LeagueType(int id, String name)
        {
            this.id = id;
            this.name = name;
        }

        @JsonCreator
        public static LeagueType from(int id)
        {
            for (LeagueType type : LeagueType.values())
            {
                if (type.getId() == id) return type;
            }
            throw new IllegalArgumentException("Invalid id");
        }

        public static LeagueType from(String name)
        {
            for (LeagueType type : LeagueType.values())
            {
                if (type.getName().equalsIgnoreCase(name)) return type;
            }
            throw new IllegalArgumentException("Invalid name");
        }

        @Override
        @JsonValue
        public int getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
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

    @Override
    public String toString()
    {
        return "BaseLeague{" + "type=" + type + ", queueType=" + queueType + ", teamType=" + teamType + '}';
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

