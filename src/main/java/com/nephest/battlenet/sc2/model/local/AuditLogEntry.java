// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class AuditLogEntry
{

    public enum Action
    {
        INSERT("I"),
        UPDATE("U"),
        DELETE("D"),
        TRUNCATE("T");

        private final String shortName;

        Action(String shortName)
        {
            this.shortName = shortName;
        }

        public static Action fromShortName(String shortName)
        {
            Objects.requireNonNull(shortName);
            for(Action action : Action.values())
                if(action.getShortName().equals(shortName)) return action;

            throw new IllegalArgumentException("Invalid shortName");
        }

        public String getShortName()
        {
            return shortName;
        }

    }

    @NotNull
    private Long id;

    @NotNull
    private OffsetDateTime created;

    @NotNull
    private String schema, table;

    @NotNull
    private Action action;

    @NotNull
    private String data, changedData;

    @Nullable
    private Long authorAccountId;

    public AuditLogEntry(){}

    public AuditLogEntry
    (
        Long id,
        OffsetDateTime created,
        String schema,
        String table,
        Action action,
        String data,
        String changedData,
        @Nullable Long authorAccountId
    )
    {
        this.id = id;
        this.created = created;
        this.schema = schema;
        this.table = table;
        this.action = action;
        this.data = data;
        this.changedData = changedData;
        this.authorAccountId = authorAccountId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof AuditLogEntry that)) {return false;}
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(id);
    }

    @Override
    public String toString()
    {
        return "AuditLogEntry{" + "id=" + id + '}';
    }

    public @NotNull Long getId()
    {
        return id;
    }

    public void setId(@NotNull Long id)
    {
        this.id = id;
    }

    public @NotNull OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(@NotNull OffsetDateTime created)
    {
        this.created = created;
    }

    public @NotNull String getSchema()
    {
        return schema;
    }

    public void setSchema(@NotNull String schema)
    {
        this.schema = schema;
    }

    public @NotNull String getTable()
    {
        return table;
    }

    public void setTable(@NotNull String table)
    {
        this.table = table;
    }

    public @NotNull Action getAction()
    {
        return action;
    }

    public void setAction(@NotNull Action action)
    {
        this.action = action;
    }

    public @NotNull String getData()
    {
        return data;
    }

    public void setData(@NotNull String data)
    {
        this.data = data;
    }

    public @NotNull String getChangedData()
    {
        return changedData;
    }

    public void setChangedData(@NotNull String changedData)
    {
        this.changedData = changedData;
    }

    @Nullable
    public Long getAuthorAccountId()
    {
        return authorAccountId;
    }

    public void setAuthorAccountId(@Nullable Long authorAccountId)
    {
        this.authorAccountId = authorAccountId;
    }

}
