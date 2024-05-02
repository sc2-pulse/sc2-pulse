// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.Application;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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
