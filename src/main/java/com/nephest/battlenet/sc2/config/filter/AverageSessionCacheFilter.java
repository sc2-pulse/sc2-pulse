// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;

@Order(-1) //pre-security order
public class AverageSessionCacheFilter
implements Filter
{

    public static final Duration CACHE_DURATION = Duration.ofSeconds(240);
    private static final String HEADER_VALUE = "private, "
        + "max-age=" + CACHE_DURATION.toSeconds() + ", "
        + "must-revalidate";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader(HttpHeaders.CACHE_CONTROL, HEADER_VALUE);
        chain.doFilter(req, resp);
    }

    @Override
    public void init(FilterConfig cfg){}

    @Override
    public void destroy(){}

}
