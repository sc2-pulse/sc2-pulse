// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.Startup;
import com.nephest.battlenet.sc2.config.openapi.SpringDocConfig;
import com.nephest.battlenet.sc2.config.security.SecurityConfig;
import com.nephest.battlenet.sc2.web.controller.AdminController;
import com.nephest.battlenet.sc2.web.controller.StatusController;
import com.nephest.battlenet.sc2.web.controller.TeamController;
import com.nephest.battlenet.sc2.web.service.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableAutoConfiguration
@ComponentScan
(
    basePackages = {"com.nephest.battlenet.sc2"},
    excludeFilters =
    {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Application.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SpringDocConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BlizzardSC2API.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BlizzardPrivacyService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StatsService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SC2WebServiceUtil.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StatusService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = AlternativeLadderService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MatchService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StatusController.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = AdminController.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TeamController.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Cron.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Startup.class)
    }
)
@Import(CoreTestConfig.class)
public class DatabaseTestConfig
{
}
