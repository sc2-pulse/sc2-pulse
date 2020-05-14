// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.Application;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableCaching
@EnableTransactionManagement
@EnableAutoConfiguration
@ComponentScan
(
    basePackages = {"com.nephest.battlenet.sc2"},
    excludeFilters =
        {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Application.class),
        }
)
@Import(CoreTestConfig.class)
public class AllTestConfig
{}
