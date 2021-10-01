// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

@Order(-1) //pre-security order
public class AverageSessionCacheFilter
implements Filter
{

    public static final Duration CACHE_DURATION = Duration.ofSeconds(240);
    private static final String MAX_AGE = "max-age=" + CACHE_DURATION.toSeconds();

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("Cache-Control", MAX_AGE);
        chain.doFilter(req, resp);
    }

    @Override
    public void init(FilterConfig cfg){}

    @Override
    public void destroy(){}

}
