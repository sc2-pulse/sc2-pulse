// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

@Component
public class ParameterBasedRobotsDenyFilter
implements Filter
{

    @Autowired
    private SeasonDAO seasonDAO;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        if(mustDeny(req))
        {
            HttpServletResponse hresp = (HttpServletResponse) resp;
            hresp.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        chain.doFilter(req, resp);
    }

    private boolean mustDeny(ServletRequest req)
    {
        return hasDeprecatedParams(req);
    }

    private boolean isOldSeason(ServletRequest req)
    {
        String seasonStr = req.getParameter("season");
        if(seasonStr == null) return false;

        int season = Integer.parseInt(seasonStr);
        return season < seasonDAO.getMaxBattlenetId();
    }

    private boolean hasDeprecatedParams(ServletRequest req)
    {
        return hasTabParam(req) || hasModalTypeParam(req) || hasDeprecatedCursor(req);
    }

    private boolean hasTabParam(ServletRequest req)
    {
        String[] tabs = req.getParameterValues("t");
        return tabs != null && tabs.length > 0;
    }

    private boolean hasModalTypeParam(ServletRequest req)
    {
        String type = req.getParameter("type");
        return type != null && type.equals("modal");
    }

    private boolean hasDeprecatedCursor(ServletRequest req)
    {
        return req.getParameter("forward") != null;
    }

}
