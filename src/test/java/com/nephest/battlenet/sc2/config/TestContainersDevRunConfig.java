// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.github.dockerjava.api.model.Bind;
import com.nephest.battlenet.sc2.model.util.TestDbInitializer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainersDevRunConfig
{

    private static final Logger LOG = LoggerFactory.getLogger(TestContainersDevRunConfig.class);

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgreSQLContainer
    (
        @Value("${org.testcontainers.postgres.image.name}") String postgresImageName,
        @Value("${org.testcontainers.dev.volume.name:#{null}}") String volumeName
    )
    {
        PostgreSQLContainer postgreSQLContainer
            = new PostgreSQLContainer(DockerImageName.parse(postgresImageName))
            .withCopyFileToContainer
            (
                MountableFile.forClasspathResource("init-db.sql"),
                "/docker-entrypoint-initdb.d/init-db.sql"
            )
            .withCopyFileToContainer
            (
                MountableFile.forClasspathResource("schema-postgres.sql"),
                "/docker-entrypoint-initdb.d/schema.sql"
            );
        if(volumeName != null)
        {
            LOG.info("Using {} volume", volumeName);
            postgreSQLContainer = postgreSQLContainer.withCreateContainerCmdModifier
            (
                cmd->cmd.getHostConfig()
                    .withBinds(Bind.parse(volumeName + ":/var/lib/postgresql/data"))
            );
            postgreSQLContainer.setWaitStrategy(createPersistentPsqlWaitStrategy());
        }
        else
        {
            LOG.info("Using ephemeral storage");
        }
        return postgreSQLContainer;
    }

    /**
     * This wait strategy fixes a
     * <a href="https://github.com/testcontainers/testcontainers-java/issues/7988">regexp issue</a>
     * in testcontainers where persistent storage log output
     * is not taken into account.
     *
     * @return wait strategy for persistent storage
     */
    public static WaitStrategy createPersistentPsqlWaitStrategy()
    {
        return new LogMessageWaitStrategy()
            .withRegEx(".*database system is ready to accept connections.*\\s")
            .withTimes(1)
            .withStartupTimeout(Duration.ofSeconds(30));
    }

    @Bean
    @ConditionalOnExpression("#{'${org.testcontainers.dev.volume.name:}'.isBlank()}")
    public ApplicationRunner dbInitializer(@Autowired TestDbInitializer testDbInitializer)
    {
        return args->
        {
            LOG.info("Setting up test DB");
            testDbInitializer.setupData();
        };
    }

}
