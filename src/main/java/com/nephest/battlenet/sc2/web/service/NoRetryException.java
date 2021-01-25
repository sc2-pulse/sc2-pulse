// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

public class NoRetryException
extends RuntimeException
{

    public NoRetryException(String msg)
    {
        super(msg);
    }

}
