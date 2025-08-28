// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.web.SortParameter;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.core.convert.ConversionService;

public class CursorParameterRedirectFilter
implements Filter
{

    public static final String MARKER_PARAMETER_NAME = "type";
    private final Map<String, Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>>> parameterOverrides;


    public CursorParameterRedirectFilter(ConversionService conversionService)
    {
        Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>>
            ladderOverrides = createLadderOverrides(conversionService);
        parameterOverrides = Map.of
        (
            "ladder",
            ladderOverrides,

            "following-ladder",
            ladderOverrides,

            "clan-search",
            createClanOverrides(conversionService)
        );
    }

    private static SortingOrder[] convertCountValuesToSortingOrderValues
    (
        String [] countValues
    )
    {
        if(countValues == null) return null;
        if(countValues.length == 0) return new SortingOrder[0];

        return Arrays.stream(countValues)
            .filter(Objects::nonNull)
            .map(Integer::parseInt)
            .map(intVal->intVal > 0 ? SortingOrder.DESC : SortingOrder.ASC)
            .toArray(SortingOrder[]::new);
    }

    private static <T extends Enum<T>> Stream<Map.Entry<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>>> convertEnumBooleanParameters
    (
        Class<T> enumm,
        Function<String, String> nameFunction,
        String convertedName,
        ConversionService conversionService
    )
    {
        return Arrays.stream(enumm.getEnumConstants())
            .map(e->{
                String name = nameFunction.apply(e.name());
                return Map.entry(
                    name,
                    params->Arrays.stream(params.get(name))
                        .anyMatch(p->p != null && p.equals("true"))
                            ? Map.entry(convertedName,
                                new String[]{conversionService.convert(e, String.class)})
                            : null
                );
            });
    }

    private static Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>> createLadderOverrides
    (
        ConversionService conversionService
    )
    {
        Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>> overrides =
        new HashMap<>(Map.of(
            "idAnchor", params->Map.entry("idCursor", params.get("idAnchor")),
            "ratingAnchor", params->Map.entry("ratingCursor", params.get("ratingAnchor")),
            "page", params->null,
            "count", params->Map.entry(
                "sort",
                Arrays.stream(convertCountValuesToSortingOrderValues(params.get("count")))
                    .map(order->new SortParameter("rating", order))
                    .map(SortParameter::toPrefixedString)
                    .toArray(String[]::new)
            )
        ));
        Stream.of
        (
            convertEnumBooleanParameters
            (
                Region.class,
                name->name.substring(0, 2).toLowerCase(),
                "region",
                conversionService
            ),
            convertEnumBooleanParameters
            (
                BaseLeague.LeagueType.class,
                name->name.substring(0, 3).toLowerCase(),
                "league",
                conversionService
            )
        )
            .flatMap(Function.identity())
            .forEach(e->overrides.put(e.getKey(), e.getValue()));
        return Collections.unmodifiableMap(overrides);
    }

    private static Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>> createClanOverrides
    (
        ConversionService conversionService
    )
    {
        return Map.of
        (
            "page", params->null,
            "pageDiff", params->Map.entry(
                "sortingOrder",
                Arrays.stream(convertCountValuesToSortingOrderValues(params.get("pageDiff")))
                    .map(order->conversionService.convert(order, String.class))
                    .toArray(String[]::new))
        );
    }

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
        if(markerParameterValue == null) return null;

        Map<String, Function<Map<String, String[]>, Map.Entry<String, String[]>>> typeParameterOverrides
            = parameterOverrides.get(markerParameterValue);
        if(typeParameterOverrides == null) return null;

        if(Collections.disjoint(params.keySet(), typeParameterOverrides.keySet())) return null;


        StringBuilder sb = new StringBuilder(httpReq.getRequestURL()).append("?");
        boolean modified = false;
        String prefix = "";
        for(Map.Entry<String, String[]> param : params.entrySet())
        {
            Function<Map<String, String[]>, Map.Entry<String, String[]>> override
                = typeParameterOverrides.get(param.getKey());
            Map.Entry<String, String[]> newParam = override != null ? override.apply(params) : param;
            if(override != null) modified = true;
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
        return modified ? sb.toString() : null;
    }

}
