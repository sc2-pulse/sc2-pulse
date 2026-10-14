// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import java.io.IOException;

public class BodyLimitExceededException
extends IOException
{

    private final long limit;

    public BodyLimitExceededException(long limit)
    {
        super("Request body limit exceeded: " + limit + " bytes");
        this.limit = limit;
    }

    public long getLimit()
    {
        return limit;
    }

}
