// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.config.data.DataConfig;
import com.nephest.battlenet.sc2.model.local.DBTestService;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.util.TestDatabaseLifecycleService;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableBatchProcessing
@EnableAutoConfiguration
@ComponentScan
(
    basePackages = {"com.nephest.battlenet.sc2"},
    useDefaultFilters = false,
    includeFilters =
    {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Repository.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SeasonGenerator.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DBTestService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TestDatabaseLifecycleService.class)
    }
)
@Import({CoreTestConfig.class, DataConfig.class, CommonBeanConfig.class})
public class DatabaseTestConfig
{
}
