// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

public class NoRetryException
extends TemplatedException
{

    public NoRetryException(String msg, String logTemplate, Object... logArgs)
    {
        super(msg, logTemplate, logArgs);
    }

    public NoRetryException(String msg)
    {
        super(msg);
    }

}
