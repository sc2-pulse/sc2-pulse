// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class BaseMatch
{

    public enum Decision
    implements Identifiable
    {

        WIN(1, "win"),
        LOSS(2, "loss"),
        TIE(3, "tie"),
        OBSERVER(4, "observer"),
        LEFT(5, "left"),
        DISAGREE(6, "disagree");

        private final int id;
        private final String name;

        Decision(int id, String name)
        {
            this.id = id;
            this.name = name;
        }

        public static Decision from(int id)
        {
            for(Decision decision : Decision.values())
            {
                if(decision.getId() == id) return decision;
            }
            throw new IllegalArgumentException("Invalid id: " + id);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Decision from(String name)
        {
            for(Decision decision : Decision.values())
            {
                if(decision.getName().equalsIgnoreCase(name)) return decision;
            }
            throw new IllegalArgumentException("Invalid name: " + name);
        }

        @Override
        public int getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

    }

    public enum MatchType
    implements Identifiable
    {
        
        _1V1(1, "1v1", TeamFormat._1V1),
        _2V2(2, "2v2", TeamFormat._2V2),
        _3V3(3, "3v3", TeamFormat._3V3),
        _4V4(4, "4v4", TeamFormat._4V4),
        ARCHON(5, "archon", TeamFormat.ARCHON),
        COOP(6, "coop", "(unknown)", null),
        CUSTOM(7, "custom", null),
        UNKNOWN(8, "", null);

        private final int id;
        private final String name;
        private final String additionalName;
        private final TeamFormat teamFormat;

        MatchType(int id, String name, String additionalName, TeamFormat teamFormat)
        {
            this.id = id;
            this.name = name;
            this.additionalName = additionalName;
            this.teamFormat = teamFormat;
        }

        MatchType(int id, String name, TeamFormat teamFormat)
        {
            this(id, name, null, teamFormat);
        }

        public static MatchType from(int id)
        {
            for(MatchType matchType : MatchType.values())
            {
                if(matchType.getId() == id) return matchType;
            }
            throw new IllegalArgumentException("Invalid id");
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static MatchType from(String name)
        {
            for(MatchType matchType : MatchType.values())
            {
                if(matchType.getName().equalsIgnoreCase(name)
                    || (matchType.getAdditionalName() != null && matchType.getAdditionalName().equalsIgnoreCase(name)))
                        return matchType;
            }
            //this is a fallback loop which is used in a very rare occasions, no reason to put the comparison into
            //the main loop
            for(MatchType matchType : MatchType.values()) if(matchType.name().equals(name)) return matchType;
            return UNKNOWN;
        }

        public static MatchType from(TeamFormat teamFormat)
        {
            if(teamFormat == null) throw new IllegalArgumentException("Invalid team format");

            for(MatchType matchType : MatchType.values())
                if(matchType.getTeamFormat() == teamFormat) return matchType;
            throw new IllegalArgumentException("Invalid team format");
        }

        @Override
        public int getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String getAdditionalName()
        {
            return additionalName;
        }

        public TeamFormat getTeamFormat()
        {
            return teamFormat;
        }

    }

    @NotNull
    private OffsetDateTime date;

    @NotNull
    private MatchType type;

    public BaseMatch(){}

    public BaseMatch
    (
        @NotNull OffsetDateTime date, @NotNull MatchType type
    )
    {
        this.date = date;
        this.type = type;
    }

    public OffsetDateTime getDate()
    {
        return date;
    }

    public void setDate(OffsetDateTime date)
    {
        this.date = date;
    }

    public MatchType getType()
    {
        return type;
    }

    public void setType(MatchType type)
    {
        this.type = type;
    }

}
