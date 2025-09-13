// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.Startup;
import com.nephest.battlenet.sc2.config.filter.FilterConfig;
import com.nephest.battlenet.sc2.config.openapi.EnumPropertyCustomizer;
import com.nephest.battlenet.sc2.config.openapi.SpringDocConfig;
import com.nephest.battlenet.sc2.config.security.BlizzardOidcUserService;
import com.nephest.battlenet.sc2.config.security.DelegatingAuthorizationRequestResolver;
import com.nephest.battlenet.sc2.config.security.DiscordOauth2UserService;
import com.nephest.battlenet.sc2.config.security.SecurityBeanConfig;
import com.nephest.battlenet.sc2.config.security.SecurityConfig;
import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.web.service.AccountService;
import com.nephest.battlenet.sc2.web.service.AlternativeLadderService;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.ClanService;
import com.nephest.battlenet.sc2.web.service.MapStatsFilmTestService;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.PersonalService;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.SC2MetaService;
import com.nephest.battlenet.sc2.web.service.SC2WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.StatusService;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import com.nephest.battlenet.sc2.web.util.ConverterUtil;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
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
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = FilterConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SecurityBeanConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DelegatingAuthorizationRequestResolver.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DiscordOauth2UserService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DiscordBootstrap.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SpringDocConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = EnumPropertyCustomizer.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BlizzardOidcUserService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = AccountService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BlizzardSC2API.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BlizzardPrivacyService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = PersonalService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SC2MetaService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ProPlayerService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StatsService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ClanService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SC2WebServiceUtil.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WebContextUtil.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ConverterUtil.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StatusService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = AlternativeLadderService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MatchService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = NotificationService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = PlayerCharacterReportService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MapStatsFilmTestService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Cron.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Startup.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Discord.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = TestConfiguration.class)
    }
)
@Import(CoreTestConfig.class)
public class DatabaseTestConfig
{
}
