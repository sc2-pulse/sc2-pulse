// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class CursorParameterRedirectFilter
implements Filter
{

    public static final String MARKER_PARAMETER_NAME = "type";
    public static final String MARKER_PARAMETER_VALUE = "ladder";
    public static final Map<String, Function<Map.Entry<String, String[]>, Map.Entry<String, String[]>>> PARAMETER_OVERRIDES =
        Map.of
        (
            "idAnchor", param->Map.entry("idCursor", param.getValue()),
            "ratingAnchor", param->Map.entry("ratingCursor", param.getValue())
        );

    @Override
    public void doFilter
    (
        ServletRequest servletRequest,
        ServletResponse servletResponse,
        FilterChain filterChain
    )
    throws IOException, ServletException
    {
        HttpServletRequest httpReq = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResp = (HttpServletResponse) servletResponse;

        String location = getRedirectLocation(httpReq);
        if(location != null)
        {
            httpResp.setHeader("Location", location);
            httpResp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            httpResp.flushBuffer();
        }
        else
        {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private String getRedirectLocation(HttpServletRequest httpReq)
    {
        Map<String, String[]> params = httpReq.getParameterMap();
        if(params.isEmpty()) return null;

        String markerParameterValue = httpReq.getParameter(MARKER_PARAMETER_NAME);
        if
        (
            markerParameterValue == null
                || !markerParameterValue.equals(MARKER_PARAMETER_VALUE)
        ) return null;
        if(Collections.disjoint(params.keySet(), PARAMETER_OVERRIDES.keySet())) return null;


        StringBuilder sb = new StringBuilder(httpReq.getRequestURL()).append("?");
        String prefix = "";
        for(Map.Entry<String, String[]> param : params.entrySet())
        {
            Map.Entry<String, String[]> newParam = PARAMETER_OVERRIDES
                .getOrDefault(param.getKey(), Function.identity())
                .apply(param);
            if(newParam == null) continue;

            if(newParam.getValue().length == 0)
            {
                sb.append(prefix).append(newParam.getKey());
                prefix = "&";
            }
            else
            {
                for(String value : newParam.getValue())
                {
                    sb.append(prefix)
                        .append(newParam.getKey())
                        .append("=")
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                    prefix = "&";
                }
            }
        }
        return sb.toString();
    }

}
