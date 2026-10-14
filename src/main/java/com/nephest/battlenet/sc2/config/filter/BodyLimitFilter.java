// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import com.nephest.battlenet.sc2.web.servlet.BodyLimitExceededException;
import com.nephest.battlenet.sc2.web.servlet.BoundedRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the body size of requests with bodies. Multipart requests are
 * ignored since they are handled by Spring.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class BodyLimitFilter
extends OncePerRequestFilter
{

    public static final Set<String> METHODS_WITH_BODY =
        Set.of("POST", "PUT", "PATCH", "DELETE");

    private final long limit;

    public BodyLimitFilter
    (
        long limitInBytes
    )
    {
        this.limit = limitInBytes;
    }

    @Autowired
    public BodyLimitFilter
    (
        @Value("${com.nephest.battlenet.sc2.request.body.limit:1MB}")
        DataSize limit
    )
    {
        this(limit.toBytes());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        if(!METHODS_WITH_BODY.contains(request.getMethod())) return true;

        String contentType = request.getContentType();
        return contentType != null
            && contentType.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal
    (
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    )
    throws ServletException, IOException
    {
        if(request.getContentLengthLong() > limit)
        {
            reject(response);
            return;
        }

        try
        {
            filterChain.doFilter
            (
                new BoundedRequestWrapper(request, limit),
                response
            );
        }
        catch(ServletException | IOException | RuntimeException ex)
        {
            if(ExceptionUtils.throwableOfType(
                ex,
                BodyLimitExceededException.class
            ) == null) throw ex;
            reject(response);
        }
    }

    private void reject(HttpServletResponse response)
    throws IOException
    {
        if(response.isCommitted()) return;

        response.resetBuffer();
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

}
