// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ParameterBasedGoneFilter
implements Filter
{

    public static final String LEGACY_UID_PARAMETER_NAME = "legacyUid";
    public static final String NEW_LEGACY_UID_MARKER = ".";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws IOException, ServletException
    {
        if(!WebServiceUtil.isApiCall((HttpServletRequest) req) && isGone(req))
        {
            ((HttpServletResponse) resp).sendError(HttpStatus.GONE.value());
            return;
        }
        chain.doFilter(req, resp);
    }

    private boolean isGone(ServletRequest req)
    {
        return isOldTeamLegacyUid(req);
    }

    private boolean isOldTeamLegacyUid(ServletRequest req)
    {
        return Optional.ofNullable(req.getParameterValues(LEGACY_UID_PARAMETER_NAME))
            .stream()
            .flatMap(Stream::of)
            .anyMatch(ParameterBasedGoneFilter::isOldTeamLegacyUid);
    }

    private static boolean isOldTeamLegacyUid(String uid)
    {
        return !uid.contains(NEW_LEGACY_UID_MARKER);
    }

}
