// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import java.time.Duration;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;

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
        hresp.setHeader("Cache-Control", HEADER_VALUE);
        chain.doFilter(req, resp);
    }

    @Override
    public void init(FilterConfig cfg){}

    @Override
    public void destroy(){}

}
