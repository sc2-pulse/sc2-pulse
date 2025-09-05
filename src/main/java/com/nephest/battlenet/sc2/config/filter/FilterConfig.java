// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.web.service.GlobalContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;

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
        registrationBean.addUrlPatterns("/api/seasons");

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<DefaultCacheFilter> defaultCacheFilter()
    {
        FilterRegistrationBean<DefaultCacheFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DefaultCacheFilter());
        registrationBean.addUrlPatterns("/api/characters/suggestions");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<VersionFilter> versionFilter()
    {
        FilterRegistrationBean<VersionFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new VersionFilter());
        registrationBean.addUrlPatterns("/api/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<NoCacheFilter> noCacheFilter()
    {
        FilterRegistrationBean<NoCacheFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new NoCacheFilter());
        registrationBean.addUrlPatterns
        (
            "/api/my/*",
            "/api/character/report/*"
        );
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<CursorParameterRedirectFilter> anchorParameterRedirectFilter
    (
        @Qualifier("mvcConversionService") ConversionService mvcConversionService,
        ObjectMapper objectMapper
    )
    {
        FilterRegistrationBean<CursorParameterRedirectFilter> registrationBean
            = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CursorParameterRedirectFilter(
            mvcConversionService, objectMapper));
        registrationBean.addUrlPatterns("/");
        return registrationBean;
    }

}
