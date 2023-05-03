// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.time.Duration;

public class RateLimitRefreshConfig
{

    private final Duration period;
    private final int slotsPerPeriod;

    public RateLimitRefreshConfig(Duration period, int slotsPerPeriod)
    {
        this.period = period;
        this.slotsPerPeriod = slotsPerPeriod;
    }

    public Duration getPeriod()
    {
        return period;
    }

    public int getSlotsPerPeriod()
    {
        return slotsPerPeriod;
    }

}
