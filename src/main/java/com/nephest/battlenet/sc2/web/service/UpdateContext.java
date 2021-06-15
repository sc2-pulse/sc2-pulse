// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import java.time.Instant;

public class UpdateContext
{

    private final Instant externalUpdate;
    private final Instant internalUpdate;

    public UpdateContext(Instant externalUpdate, Instant internalUpdate)
    {
        this.externalUpdate = externalUpdate;
        this.internalUpdate = internalUpdate;
    }

    public UpdateContext()
    {
        this(null, null);
    }

    public Instant getExternalUpdate()
    {
        return externalUpdate;
    }

    public Instant getInternalUpdate()
    {
        return internalUpdate;
    }

}
