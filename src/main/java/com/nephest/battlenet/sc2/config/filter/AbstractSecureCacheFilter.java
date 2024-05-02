// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

public abstract class AbstractSecureCacheFilter
implements Filter
{

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        if(!isSecure(hresp))
        {
            NoCacheFilter.NO_CACHE_HEADERS.forEach(hresp::setHeader);
        }
        else
        {
            setCache(hresp);
        }
        chain.doFilter(req, resp);
    }

    public boolean isSecure(HttpServletResponse resp)
    {
        return resp.getHeader(HttpHeaders.SET_COOKIE) == null;
    }

    public abstract void setCache(HttpServletResponse resp);

}
