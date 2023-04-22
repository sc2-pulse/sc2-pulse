// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

//this filter protects personal data when SpringSecurity is disabled
public class NoCacheFilter
implements Filter
{

    public static final Map<String, String> NO_CACHE_HEADERS = Map.of
    (
        HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate",
        HttpHeaders.PRAGMA, "no-cache",
        HttpHeaders.EXPIRES, "0"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        NO_CACHE_HEADERS.forEach(hresp::setHeader);
        chain.doFilter(req, resp);
    }
}
