// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

public class BaseAPI
{

    private WebClient client;
    private Retry retry;

    protected void setWebClient(WebClient client)
    {
        this.client = client;
    }

    public WebClient getWebClient()
    {
        return client;
    }

    protected void setRetry(Retry retry)
    {
        this.retry = retry;
    }

    public Retry getRetry(Retry def)
    {
        return retry != null ? retry : def;
    }

}
