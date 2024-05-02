// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class TestFilterConfig
{

    @Bean
    public FilterRegistrationBean<AverageSessionCacheFilter> testCacheFilter()
    {
        FilterRegistrationBean<AverageSessionCacheFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new AverageSessionCacheFilter());
        registrationBean.addUrlPatterns("/api/test/cache", "/api/test/cache/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TestCookieFilter> testCacheCookieFilter()
    {
        FilterRegistrationBean<TestCookieFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new TestCookieFilter());
        registrationBean.addUrlPatterns("/api/test/cache/cookie");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }

}
