// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TeamType
implements Identifiable
{

    ARRANGED(0, "Arranged"), RANDOM(1, "Random");

    private final int id;
    private final String name;

    TeamType(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    @JsonCreator
    public static TeamType from(int id)
    {
        for (TeamType type : TeamType.values())
        {
            if (type.getId() == id) return type;
        }
        throw new IllegalArgumentException("Invalid id");
    }

    public static TeamType from(String name)
    {
        for(TeamType type : TeamType.values())
            if(type.getName().equalsIgnoreCase(name)) return type;

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

