// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.config.convert.IdentifiableToIntegerConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToClanMemberEventTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToDecisionConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToLeagueTierTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToLeagueTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToMatchTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToPlayerCharacterReportTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToQueueTypeConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToRaceConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToRegionConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToSC2PulseAuthority;
import com.nephest.battlenet.sc2.config.convert.IntegerToSocialMediaConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToTeamTypeConverter;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class CoreTestConfig
{

    public static final Duration IO_TIMEOUT = Duration.ofMillis(500);

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
        service.addConverter(new IntegerToSocialMediaConverter());
        service.addConverter(new IntegerToMatchTypeConverter());
        service.addConverter(new IntegerToDecisionConverter());
        service.addConverter(new IntegerToSC2PulseAuthority());
        service.addConverter(new IntegerToPlayerCharacterReportTypeConverter());
        service.addConverter(new IntegerToClanMemberEventTypeConverter());
        return service;
    }

    @Bean
    public Random simpleRng()
    {
        return new Random();
    }

    @Bean
    public RestTemplateCustomizer restTemplateCustomizer() {
        return new GlobalRestTemplateCustomizer((int) IO_TIMEOUT.toMillis());
    }

    @Bean
    public ExecutorService dbExecutorService()
    {
        return Executors.newFixedThreadPool(Application.DB_THREADS);
    }

    @Bean
    public ExecutorService webExecutorService()
    {
        return new ThreadPoolExecutor
        (
            Application.CORE_WEB_THREADS,
            Application.CORE_WEB_THREADS + Application.BACKGROUND_WEB_THREADS,
            Application.WEB_THREAD_TTL_SECONDS, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new CustomizableThreadFactory(Application.WEB_THREAD_POOL_NAME)
        );
    }

}
