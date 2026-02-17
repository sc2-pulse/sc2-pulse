// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.data.clickhouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Objects;

public class ClickHouseConnectionDetailsImpl
implements ClickHouseConnectionDetails
{

    @NotNull
    private URI endpoint;

    @NotBlank
    private String database;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public ClickHouseConnectionDetailsImpl()
    {
    }

    public ClickHouseConnectionDetailsImpl
    (
        URI endpoint,
        String database,
        String username,
        String password
    )
    {
        this.endpoint = endpoint;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof ClickHouseConnectionDetailsImpl that)) {return false;}
        return Objects.equals(getEndpoint(), that.getEndpoint())
            && Objects.equals(getDatabase(), that.getDatabase())
            && Objects.equals(getUsername(), that.getUsername())
            && Objects.equals(getPassword(), that.getPassword());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getEndpoint(), getDatabase(), getUsername(), getPassword());
    }

    @Override
    public String toString()
    {
        return "ClickHouseConnectionDetailsImpl{"
            + "endpoint=" + endpoint
            + ", database='" + database + '\''
            + ", username='" + username + '\''
            + ", password='*'"
            + '}';
    }

    @Override
    public URI getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(URI endpoint)
    {
        this.endpoint = endpoint;
    }

    @Override
    public String getDatabase()
    {
        return database;
    }

    public void setDatabase(String database)
    {
        this.database = database;
    }

    @Override
    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    @Override
    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

}
