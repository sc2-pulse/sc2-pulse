// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.Application;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class VersionFilter
implements Filter
{
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws ServletException, IOException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("X-Application-Version", Application.VERSION);
        chain.doFilter(req, resp);
    }
}
