// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/api/*")
public class ApiFilter
implements Filter
{

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("Cache-Control", "max-age=3600");
        chain.doFilter(req, resp);
    }

    @Override
    public void init(FilterConfig cfg){}

    @Override
    public void destroy(){}

}
