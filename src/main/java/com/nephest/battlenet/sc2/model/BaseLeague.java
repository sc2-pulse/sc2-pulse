// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;

public class BaseLeague
{

    public enum LeagueType
    implements Identifiable, MultiAliasName
    {

        BRONZE(0, "bronze", Set.of("metal")),
        SILVER(1, "silver", Set.of("metal")),
        GOLD(2, "gold", Set.of("metal")),
        PLATINUM(3, "platinum", Set.of("plat", "metal")),
        DIAMOND(4, "diamond", Set.of()),
        MASTER(5, "master", Set.of("masters")),
        GRANDMASTER(6, "grandmaster", Set.of("GM", "grandmasters", "grand master"));

        public static final Map<LeagueType, Set<String>> ALL_NAMES_MAP =
            MiscUtil.generateAllNamesMap(LeagueType.class);

        private final int id;
        private final String name;
        private final Set<String> additionalNames;

        LeagueType(int id, String name, Set<String> additionalNames)
        {
            this.id = id;
            this.name = name;
            this.additionalNames = additionalNames;
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
            return optionalFrom(name).orElseThrow();
        }

        public static Optional<LeagueType> optionalFrom(String name)
        {
            String lowerName = name.toLowerCase();
            for (LeagueType type : LeagueType.values())
                if (type.getName().equalsIgnoreCase(lowerName)) return Optional.of(type);
            return Optional.empty();
        }

        @Override
        @JsonValue
        public int getId()
        {
            return id;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public Set<String> getAdditionalNames()
        {
            return additionalNames;
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

