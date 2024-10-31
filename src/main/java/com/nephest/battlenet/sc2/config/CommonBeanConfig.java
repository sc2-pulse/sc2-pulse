// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.config.convert.AuditLogEntryActionToStringConverter;
import com.nephest.battlenet.sc2.config.convert.IdentifiableToIntegerConverter;
import com.nephest.battlenet.sc2.config.convert.IntegerToAccountPropertyTypeConverter;
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
import com.nephest.battlenet.sc2.config.convert.StringToAuditLogEntryActionConverter;
import com.nephest.battlenet.sc2.config.convert.min.IdentifiableToMinimalObjectConverter;
import com.nephest.battlenet.sc2.config.convert.min.TimestampToMinimalObjectConverter;
import com.nephest.battlenet.sc2.model.Region;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class CommonBeanConfig
{

    /*
        Don't change the DB thread count. The DB code doesn't handle concurrent transactions, so deadlocks can happen.
        There is no need to have more than 1 thread here because most of the CPU work is done on the DB side, so
        there is no need to properly handle concurrency because it will waste CPU resources without any performance
        boost whatsoever. Concurrency/CPU intensive work is done by web threads.
     */
    public static final int DB_THREADS = 1;
    public static final int CORE_WEB_THREADS = Region.values().length;
    public static final int BACKGROUND_WEB_THREADS = 15;
    public static final int WEB_THREAD_TTL_SECONDS = 60;
    public static final String WEB_THREAD_POOL_NAME = "p-web-";

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
        service.addConverter(new IntegerToAccountPropertyTypeConverter());
        return service;
    }

    @Bean
    public ConversionService auditLogConversionService()
    {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        service.addConverter(new StringToAuditLogEntryActionConverter());
        service.addConverter(new AuditLogEntryActionToStringConverter());
        return service;
    }

    /**
     * minimalConversionService converts objects to their simplest, shortest form, both memory
     * and
     * text (JSON) wise. It always converts to {@link Object}, so you have to use the
     * corresponding class in {@link ConversionService#convert(Object, Class)} and related methods,
     * including arrays and collections.
     * </p>
     * Converted value may not fully represent the original object, some details such as
     * precision or meta info may be dropped, so reverse conversion may be impossible.
     * </p>
     *
     * @return conversion service
     */
    @Bean
    public ConversionService minimalConversionService()
    {
        DefaultConversionService service = new DefaultConversionService();
        service.addConverter(new TimestampToMinimalObjectConverter());
        service.addConverter(new IdentifiableToMinimalObjectConverter());
        return service;
    }

    @Bean
    public ExecutorService dbExecutorService()
    {
        return Executors.newFixedThreadPool(DB_THREADS);
    }

    @Bean
    public ExecutorService secondaryDbExecutorService()
    {
        return Executors.newFixedThreadPool(DB_THREADS);
    }

    @Bean
    public ExecutorService webExecutorService()
    {
        return new ThreadPoolExecutor
        (
            CORE_WEB_THREADS,
            CORE_WEB_THREADS + BACKGROUND_WEB_THREADS,
            WEB_THREAD_TTL_SECONDS, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new CustomizableThreadFactory(WEB_THREAD_POOL_NAME)
        );
    }

    @Bean
    public Scheduler dbScheduler(@Qualifier("dbExecutorService") ExecutorService executorService)
    {
        return Schedulers.fromExecutorService(executorService, "DB scheduler");
    }

    @Bean
    public Scheduler secondaryDbScheduler
    (
        @Qualifier("secondaryDbExecutorService") ExecutorService executorService
    )
    {
        return Schedulers.fromExecutorService(executorService, "Secondary DB scheduler");
    }

}
