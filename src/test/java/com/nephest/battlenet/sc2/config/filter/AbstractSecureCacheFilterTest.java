// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

/**
 * Base class for cache filters. Sets cache headers only when it is safe to do so,
 * {@link NoCacheFilter#NO_CACHE_HEADERS} is used otherwise. Insecure responses are responses that
 * can lead to side effect when cached, such as protected resources, headers, and so on.
 */
@ExtendWith(MockitoExtension.class)
public class AbstractSecureCacheFilterTest
{

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private AbstractSecureCacheFilter filter;
    private boolean cacheWasSet;

    @BeforeEach
    public void beforeEach()
    {
        cacheWasSet = false;
        filter = new AbstractSecureCacheFilter()
        {
            @Override
            public void setCache(HttpServletResponse resp)
            {
                cacheWasSet = true;
            }
        };
    }

    @Test
    public void whenSecureResponse_thenCache()
    throws ServletException, IOException
    {
        assertTrue(filter.isSecure(response));
        filter.doFilter(request, response, filterChain);
        verify(response, never()).setHeader(any(), any());
        assertTrue(cacheWasSet);
    }

    @Test
    public void whenCookiesAreEncountered_thenDontCache()
    throws ServletException, IOException
    {
        when(response.getHeader(HttpHeaders.SET_COOKIE)).thenReturn("cookie");
        assertFalse(filter.isSecure(response));
        filter.doFilter(request, response, filterChain);
        NoCacheFilter.NO_CACHE_HEADERS.forEach((key, value)->verify(response).setHeader(key, value));
        assertFalse(cacheWasSet);
    }

}
