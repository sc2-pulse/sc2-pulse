// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class StatefulRestTemplateInterceptor
implements ClientHttpRequestInterceptor
{

    private String cookie;

    @Override
    public @NotNull ClientHttpResponse intercept
    (
        @NotNull HttpRequest request,
        byte @NotNull [] body,
        @NotNull ClientHttpRequestExecution execution
    )
    throws IOException
    {
        if (cookie != null)
            request.getHeaders().add(HttpHeaders.COOKIE, cookie);
        ClientHttpResponse response = execution.execute(request, body);
        if (cookie == null)
            cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return response;
    }

}
