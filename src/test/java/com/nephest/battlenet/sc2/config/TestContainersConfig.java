// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
@Import({TestContainersCommonConfig.class})
public class TestContainersConfig
{

    @Autowired
    private Network network;

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgreSQLContainer
    (
        @Value("${org.testcontainers.postgres.image.name}") String postgresImageName
    )
    {
        return TestContainersUtil
            .createPostgreSQLContainer(postgresImageName, network)
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,noexec,nosuid,size=512m"));
    }

    @Bean
    @ServiceConnection
    public ClickHouseContainer clickHouseContainer
    (
        @Value("${org.testcontainers.clickhouse.image.name}") String clickHouseImageName
    )
    {
        return TestContainersUtil
            .createClickHouseContainer(clickHouseImageName, network)
            .withTmpFs(Map.of("/var/lib/clickhouse", "rw,noexec,nosuid,size=512m"));
    }

}
