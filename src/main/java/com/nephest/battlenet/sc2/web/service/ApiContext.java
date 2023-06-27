// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;

public class ApiContext
{

    private final Iterable<ReactorRateLimiter> rateLimiters;
    private final APIHealthMonitor healthMonitor;
    private final String baseUrl;

    public ApiContext(Iterable<ReactorRateLimiter> rateLimiters, APIHealthMonitor healthMonitor, String baseUrl)
    {
        this.rateLimiters = rateLimiters;
        this.healthMonitor = healthMonitor;
        this.baseUrl = baseUrl;
    }

    public Iterable<ReactorRateLimiter> getRateLimiters()
    {
        return rateLimiters;
    }

    public APIHealthMonitor getHealthMonitor()
    {
        return healthMonitor;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

}
