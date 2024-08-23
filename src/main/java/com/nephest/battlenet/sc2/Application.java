// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import com.nephest.battlenet.sc2.config.GlobalRestTemplateCustomizer;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableCaching
@EnableRetry
@EnableScheduling
@EnableTransactionManagement
@PropertySource(value = "classpath:application-private.properties", ignoreResourceNotFound = true)
public class Application
extends SpringBootServletInitializer
{

    public static final String VERSION = Application.class.getPackage().getImplementationVersion() != null
        ? Application.class.getPackage().getImplementationVersion()
        : "unknown";

    public static void main(String[] args)
    {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ClientHttpConnector clientHttpConnector()
    {
        return WebServiceUtil.getClientHttpConnector();
    }

    @Bean
    public RestTemplateCustomizer restTemplateCustomizer() {
        return new GlobalRestTemplateCustomizer();
    }

}
