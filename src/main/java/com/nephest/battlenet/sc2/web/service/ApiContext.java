// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;

public class ApiContext
{

    private final ReactorRateLimiter rateLimiter;
    private final APIHealthMonitor healthMonitor;
    private final String baseUrl;

    public ApiContext(ReactorRateLimiter rateLimiter, APIHealthMonitor healthMonitor, String baseUrl)
    {
        this.rateLimiter = rateLimiter;
        this.healthMonitor = healthMonitor;
        this.baseUrl = baseUrl;
    }

    public ReactorRateLimiter getRateLimiter()
    {
        return rateLimiter;
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
