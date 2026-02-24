// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.data;

import static com.nephest.battlenet.sc2.model.util.PostgreSQLUtils.CONTAINER_HOST;
import static com.nephest.battlenet.sc2.model.util.PostgreSQLUtils.CONTAINER_PORT;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.container.ContainerInfo;
import com.nephest.battlenet.sc2.config.data.clickhouse.ClickHouseConnectionDetails;
import com.nephest.battlenet.sc2.config.data.clickhouse.ClickHouseConnectionDetailsImpl;
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataConfig
{

    @Bean
    public Properties postgresUrlProperties
    (
        JdbcConnectionDetails jdbcConnectionDetails,
        @Autowired(required = false)
        @Qualifier("postgreSQLContainerInfo")
        ContainerInfo postgreSQLContainerInfo
    )
    {
        Properties props =  Driver.parseURL(jdbcConnectionDetails.getJdbcUrl(), null);
        if(postgreSQLContainerInfo != null)
        {
            props.put(CONTAINER_HOST, postgreSQLContainerInfo.host());
            props.put(CONTAINER_PORT, postgreSQLContainerInfo.port());
        }
        return props;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource)
    {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate sc2StatsNamedTemplate(DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /*TODO
        This temporarily fixes https://github.com/spring-projects/spring-batch/issues/4519.
        Spring fix should be available in Batch 5.2. This fix should be removed when Spring fix
        is released in Spring  Boot.
     */
    @Bean
    public static BeanDefinitionRegistryPostProcessor jobRegistryBeanPostProcessorRemover()
    {
        return registry -> registry.removeBeanDefinition("jobRegistryBeanPostProcessor");
    }

    @Bean
    @ConditionalOnProperty
    (
        prefix = "com.nephest.battlenet.sc2.datasource.native.clickhouse",
        name = {"endpoint", "database", "username", "password"}
    )
    @ConditionalOnMissingBean(ClickHouseConnectionDetails.class)
    @ConfigurationProperties("com.nephest.battlenet.sc2.datasource.native.clickhouse")
    public ClickHouseConnectionDetails clickHouseConnectionDetails()
    {
        return new ClickHouseConnectionDetailsImpl();
    }

    @Bean
    public Client clickHouseClient(ClickHouseConnectionDetails details)
    {
        return new Client.Builder()
            .addEndpoint(details.getEndpoint().toString())
            .setDefaultDatabase(details.getDatabase())
            .setUsername(details.getUsername())
            .setPassword(details.getPassword())
            .build();
    }

}
