// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

public class RobotsDenyFilter
implements Filter
{

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("X-Robots-Tag", "noindex, nofollow");
        chain.doFilter(req, resp);
    }

}
