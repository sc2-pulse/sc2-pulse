// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
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
            .withInitScript("init-db.sql");
    }

}
