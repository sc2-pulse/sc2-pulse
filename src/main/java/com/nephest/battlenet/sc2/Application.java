// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import com.nephest.battlenet.sc2.config.convert.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
@PropertySource("classpath:application-private.properties")
public class Application
extends SpringBootServletInitializer
{

    public static void main(String[] args)
    {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public PlatformTransactionManager txManager(DataSource dataSource)
    {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate sc2StatsNamedTemplate(DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public ConversionService sc2StatsConversionService()
    {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        service.addConverter(new IdentifiableToIntegerConverter());
        service.addConverter(new IntegerToQueueTypeConverter());
        service.addConverter(new IntegerToLeagueTierTypeConverter());
        service.addConverter(new IntegerToLeagueTypeConverter());
        service.addConverter(new IntegerToRegionConverter());
        service.addConverter(new IntegerToTeamTypeConverter());
        service.addConverter(new IntegerToRaceConverter());
        return service;
    }

}
