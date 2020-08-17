// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("maintenance")
public class MaintenanceFilter
implements Filter
{
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "The server is a maintenance mode");
    }
}
