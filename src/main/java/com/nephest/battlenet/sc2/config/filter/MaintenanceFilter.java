// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("maintenance")
public class MaintenanceFilter
implements Filter
{
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletRequest hreq = (HttpServletRequest) req;
        HttpServletResponse hresp = (HttpServletResponse) resp;
        if
        (
            hreq.getRequestURI().startsWith(hreq.getContextPath() + "/static")
            || hreq.getRequestURI().startsWith(hreq.getContextPath() + "/webjars")
        )
        {
            chain.doFilter(req, resp);
        }
        else
        {
            hresp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "The server is a maintenance mode");
        }
    }
}
