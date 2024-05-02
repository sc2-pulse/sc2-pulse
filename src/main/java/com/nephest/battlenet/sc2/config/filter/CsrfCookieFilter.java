// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CsrfCookieFilter
extends OncePerRequestFilter
{

    @Override
    protected void doFilterInternal
    (
        HttpServletRequest request,
        @NotNull HttpServletResponse response,
        FilterChain filterChain
    )
    throws ServletException, IOException
    {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        // Render the token value to a cookie by causing the deferred token to be loaded
        csrfToken.getToken();

        filterChain.doFilter(request, response);
    }

}
