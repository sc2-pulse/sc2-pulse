// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
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
