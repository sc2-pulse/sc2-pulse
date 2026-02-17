// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.data.clickhouse;


import java.net.URI;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.testcontainers.clickhouse.ClickHouseContainer;

public class ClickHouseServiceConnectionFactory
extends ContainerConnectionDetailsFactory<ClickHouseContainer, ClickHouseConnectionDetails>
{

    @Override
    protected ClickHouseConnectionDetails getContainerConnectionDetails
    (
        ContainerConnectionSource<ClickHouseContainer> source
    )
    {
        return new ClickHouseContainerConnectionDetails(source);
    }

    private static final class ClickHouseContainerConnectionDetails
    extends ContainerConnectionDetails<ClickHouseContainer>
    implements ClickHouseConnectionDetails
    {

        private ClickHouseContainerConnectionDetails
        (
            ContainerConnectionSource<ClickHouseContainer> source
        )
        {
            super(source);
        }

        @Override
        public URI getEndpoint()
        {
            return URI.create(getContainer().getHttpUrl());
        }

        @Override
        public String getDatabase()
        {
            return getContainer().getDatabaseName();
        }

        @Override
        public String getUsername()
        {
            return getContainer().getUsername();
        }

        @Override
        public String getPassword()
        {
            return getContainer().getPassword();
        }

    }
}
