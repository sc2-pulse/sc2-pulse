// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TeamType
implements Identifiable
{

    ARRANGED(0), RANDOM(1);

    private final int id;

    TeamType(int id)
    {
        this.id = id;
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

    @Override
    @JsonValue
    public int getId()
    {
        return id;
    }

}

