// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class BaseLeagueTier
{

    public enum LeagueTierType
    implements Identifiable
    {

        FIRST(0, "1"), SECOND(1, "2"), THIRD(2, "3");

        private final int id;
        private final String name;

        LeagueTierType(int id, String name)
        {
            this.id = id;
            this.name = name;
        }

        @JsonCreator
        public static LeagueTierType from(int id)
        {
            for (LeagueTierType type : LeagueTierType.values())
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

        public String getName()
        {
            return name;
        }

    }

    private LeagueTierType type;

    private Integer minRating;
    private Integer maxRating;

    public BaseLeagueTier(){}

    public BaseLeagueTier(LeagueTierType type, Integer minRating, Integer maxRating)
    {
        this.type = type;
        this.minRating = minRating;
        this.maxRating = maxRating;
    }

    public void setType(LeagueTierType type)
    {
    this.type = type;
    }

    public LeagueTierType getType()
    {
        return type;
    }

    public void setMinRating(Integer minRating)
    {
        this.minRating = minRating;
    }

    public Integer getMinRating()
    {
        return minRating;
    }

    public void setMaxRating(Integer maxRating)
    {
        this.maxRating = maxRating;
    }

    public Integer getMaxRating()
    {
        return maxRating;
    }

}


