// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/")
public class OldSeasonRobotsDenyFilter
implements Filter
{

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        String seasonStr = req.getParameter("season");
        if(seasonStr == null) {chain.doFilter(req, resp); return;}

        int season = Integer.parseInt(seasonStr);
        if(season < BlizzardSC2API.LAST_SEASON)
        {
            HttpServletResponse hresp = (HttpServletResponse) resp;
            hresp.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        chain.doFilter(req, resp);
    }

}
