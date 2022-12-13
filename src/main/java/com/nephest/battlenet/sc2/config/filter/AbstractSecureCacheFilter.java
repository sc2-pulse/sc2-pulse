// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractSecureCacheFilter
implements Filter
{

    private static final String TARGET_HEADER = "Set-Cookie";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        if(!isSecure(hresp))
        {
            hresp.setHeader("Cache-Control", NoCacheFilter.NO_CACHE_HEADER);
        }
        else
        {
            setCache(hresp);
        }
        chain.doFilter(req, resp);
    }

    public boolean isSecure(HttpServletResponse resp)
    {
        return resp.getHeader(TARGET_HEADER) == null;
    }

    public abstract void setCache(HttpServletResponse resp);

}
