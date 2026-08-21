// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public final class TestContainersUtil
{

    private TestContainersUtil(){}

    public static PostgreSQLContainer createPostgreSQLContainer
    (
        String imageName,
        Network network
    )
    {
        return new PostgreSQLContainer(DockerImageName.parse(imageName))
            .withCopyFileToContainer
            (
                MountableFile.forClasspathResource("init-db.sql"),
                "/docker-entrypoint-initdb.d/init-db.sql"
            )
            .withCopyFileToContainer
            (
                MountableFile.forClasspathResource("schema-postgres.sql"),
                "/docker-entrypoint-initdb.d/schema.sql"
            )
            .withNetwork(network);
    }

    public static ClickHouseContainer createClickHouseContainer
    (
        String imageName,
        Network network
    )
    {
        return new ClickHouseContainer(imageName)
            .withCopyFileToContainer
            (
                MountableFile.forClasspathResource("schema-clickhouse.sql"),
                "/docker-entrypoint-initdb.d/schema.sql"
            )
            .withNetwork(network);
    }

}
