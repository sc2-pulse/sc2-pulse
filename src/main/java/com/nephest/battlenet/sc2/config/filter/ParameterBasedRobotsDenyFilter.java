// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

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
        return isOldSeason(req) || !isAllowedCharacterParams(req);
    }

    private boolean isOldSeason(ServletRequest req)
    {
        String seasonStr = req.getParameter("season");
        if(seasonStr == null) return false;

        int season = Integer.parseInt(seasonStr);
        return season < seasonDAO.getMaxBattlenetId();
    }

    private boolean isAllowedCharacterParams(ServletRequest req)
    {
        boolean result = true;
        if(isCharacterType(req)) result = isCharacterSummaryTabExclusively(req);
        return result;
    }

    private boolean isCharacterType(ServletRequest req)
    {
        String type = req.getParameter("type");
        return type != null && type.equals("character");
    }

    private boolean isCharacterHistoryTab(ServletRequest req)
    {
        String[] tabs = req.getParameterValues("t");
        return tabs != null && Arrays.asList(tabs).contains("player-stats-history");
    }

    private boolean isCharacterSummaryTabExclusively(ServletRequest req)
    {
        String[] tabs = req.getParameterValues("t");
        return tabs != null && tabs.length == 1 && tabs[0].equals("player-stats-summary");
    }

}
