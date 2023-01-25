// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.web.service.GlobalContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class FilterConfig
{

    @Bean
    public FilterRegistrationBean<AverageSessionCacheFilter> sessionWebCacheFilter()
    {
        FilterRegistrationBean<AverageSessionCacheFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new AverageSessionCacheFilter());
        registrationBean.addUrlPatterns("/api/ladder/stats/*");

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<SeasonCacheFilter> seasonCacheFilter
    (
        @Autowired SeasonDAO seasonDAO,
        @Autowired GlobalContext globalContext
    )
    {
        FilterRegistrationBean<SeasonCacheFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new SeasonCacheFilter(seasonDAO, globalContext.getActiveRegions()));
        registrationBean.addUrlPatterns("/api/season/list", "/api/season/list/all");

        return registrationBean;
    }

    @Bean @Profile("maintenance")
    public FilterRegistrationBean<MaintenanceFilter> maintenanceFilterRegistration()
    {
        FilterRegistrationBean<MaintenanceFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new MaintenanceFilter());
        reg.addUrlPatterns("/*");
        return reg;
    }

}
