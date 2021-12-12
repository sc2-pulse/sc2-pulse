// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.RetrySpec;

public class BaseAPI
{

    private WebClient client;
    private RetrySpec retry;

    protected void setWebClient(WebClient client)
    {
        this.client = client;
    }

    public WebClient getWebClient()
    {
        return client;
    }

    protected void setRetry(RetrySpec retry)
    {
        this.retry = retry;
    }

    public RetrySpec getRetry(RetrySpec def)
    {
        return retry != null ? retry : def;
    }

}
