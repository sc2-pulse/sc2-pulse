// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.math.BigDecimal;
import java.time.Instant;

public class RateLimitData
{

    public static final BigDecimal THOUSAND = new BigDecimal(1000);

    private final int limit, remaining;
    private final Instant reset;

    public RateLimitData(int limit, int remaining, Instant reset)
    {
        this.limit = limit;
        this.remaining = remaining;
        this.reset = reset;
    }

    public RateLimitData(int limit, int remaining, String reset)
    {
        this(limit, remaining, parseInstant(reset));
    }

    public static long parseMillis(String str)
    {
        return new BigDecimal(str).multiply(THOUSAND).longValue();
    }

    public static Instant parseInstant(String str)
    {
        return Instant.ofEpochMilli(parseMillis(str));
    }

    public int getLimit()
    {
        return limit;
    }

    public int getRemaining()
    {
        return remaining;
    }

    public Instant getReset()
    {
        return reset;
    }

}
