// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@Import({TestContainersCommonConfig.class})
public class TestContainersConfig
{

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgreSQLContainer
    (
        @Value("${org.testcontainers.postgres.image.name}") String postgresImageName
    )
    {
        return new PostgreSQLContainer(DockerImageName.parse(postgresImageName))
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,noexec,nosuid,size=512m"))
            .withInitScript("init-db.sql")
            .withNetwork(Network.SHARED);
    }

    @Bean
    @ServiceConnection
    public ClickHouseContainer clickHouseContainer
    (
        @Value("${org.testcontainers.clickhouse.image.name}") String clickHouseImageName
    )
    {
        return new ClickHouseContainer(clickHouseImageName)
            .withTmpFs(Map.of("/var/lib/clickhouse", "rw,noexec,nosuid,size=512m"))
            .withNetwork(Network.SHARED);
    }

}
