// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.nephest.battlenet.sc2.model.Identifiable;
import jakarta.validation.constraints.NotNull;

public class ConnectionMetaData
{

    public enum Type
    implements Identifiable
    {
        INTEGER_LESS_THAN_OR_EQUAL(1),
        INTEGER_GREATER_THAN_OR_EQUAL(2),
        INTEGER_EQUAL(3),
        INTEGER_NOT_EQUAL(4),
        DATETIME_LESS_THAN_OR_EQUAL(5),
        DATETIME_GREATER_THAN_OR_EQUAL(6),
        BOOLEAN_EQUAL(7),
        BOOLEAN_NOT_EQUAL(8);

        private final int id;

        Type(int id)
        {
            this.id = id;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Type from(int id)
        {
            for (Type type : Type.values())
                if (type.getId() == id) return type;

            throw new IllegalArgumentException("Invalid id: " + id);
        }

        @JsonValue
        @Override
        public int getId()
        {
            return id;
        }

    }

    @NotNull
    private final Type type;

    @NotNull
    private final String key, name, description;

    public ConnectionMetaData(Type type, String key, String name, String description)
    {
        this.type = type;
        this.key = key;
        this.name = name;
        this.description = description;
    }

    public Type getType()
    {
        return type;
    }

    public String getKey()
    {
        return key;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

}
