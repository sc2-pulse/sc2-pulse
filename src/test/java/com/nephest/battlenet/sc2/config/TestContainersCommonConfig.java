// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.config.container.ContainerInfo;
import com.nephest.battlenet.sc2.testcontainers.ExternalNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainersCommonConfig
{

    @Bean
    public ContainerInfo postgreSQLContainerInfo(PostgreSQLContainer postgreSQLContainer)
    {
        return new ContainerInfo
        (
            TestContainersUtil.getSanitizedContainerName(postgreSQLContainer),
            postgreSQLContainer.getExposedPorts().get(0)
        );
    }

    @Bean
    public Network testContainersNetwork
    (
        @Value("${org.testcontainers.network.external.name:#{null}}") String networkName
    )
    {
        return networkName != null ? new ExternalNetwork(networkName) : Network.SHARED;
    }

}
