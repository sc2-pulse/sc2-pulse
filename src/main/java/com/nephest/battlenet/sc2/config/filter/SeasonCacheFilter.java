// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

public class SeasonCacheFilter
implements Filter
{

    private final SeasonDAO seasonDAO;

    public SeasonCacheFilter(SeasonDAO seasonDAO)
    {
        this.seasonDAO = seasonDAO;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        String cacheHeader = getCacheHeader();
        if(cacheHeader != null)
        {
            hresp.setHeader(HttpHeaders.CACHE_CONTROL, cacheHeader);
        }
        else
        {
            NoCacheFilter.NO_CACHE_HEADERS.forEach(hresp::setHeader);
        }

        chain.doFilter(req, resp);
    }

    private String getCacheHeader()
    {
        List<Season> currentSeasons = seasonDAO.findListByBattlenetId(seasonDAO.getMaxBattlenetId());
        String cacheHeader;
        if(currentSeasons.size() == Region.values().length)
        {
            Duration cacheDuration = Duration.between
            (
                LocalDateTime.now(),
                currentSeasons.stream()
                    .map(Season::getEnd)
                    .map(LocalDate::atStartOfDay)
                    .min(Comparator.naturalOrder())
                    .orElseThrow()
            );
            cacheHeader = !cacheDuration.isNegative()
                ? "private, "
                    + "max-age=" + cacheDuration.toSeconds() + ", "
                    + "must-revalidate"
                : null;
        }
        else
        {
            cacheHeader = null;
        }

        return cacheHeader;
    }

}
