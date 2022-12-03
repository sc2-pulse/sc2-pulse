// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

//this filter protects personal data when SpringSecurity is disabled
@WebFilter({"/api/my/*", "/api/character/report/*"})
public class NoCacheFilter
implements Filter
{

    public static final String NO_CACHE_HEADER = "no-cache, no-store, must-revalidate";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("Cache-Control", NO_CACHE_HEADER);
        chain.doFilter(req, resp);
    }
}
