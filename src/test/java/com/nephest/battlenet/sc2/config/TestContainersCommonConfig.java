// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.config.container.ContainerInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainersCommonConfig
{

    @Bean
    public ContainerInfo postgreSQLContainerInfo(PostgreSQLContainer postgreSQLContainer)
    {
        return new ContainerInfo
        (
            postgreSQLContainer.getNetworkAliases().get(0),
            postgreSQLContainer.getExposedPorts().get(0)
        );
    }

}
