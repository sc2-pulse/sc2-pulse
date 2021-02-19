// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

public class TemplatedException
extends RuntimeException
{

    private final String logTemplate;
    private final Object[] logArgs;

    public TemplatedException(String msg, String logTemplate, Object... logArgs)
    {
        super(msg);
        this.logTemplate = logTemplate;
        this.logArgs = logArgs;
    }

    public TemplatedException(String msg)
    {
        this(msg, msg);
    }

    public String getLogTemplate()
    {
        return logTemplate;
    }

    public Object[] getLogArgs()
    {
        return logArgs;
    }
}
