// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import java.time.Instant;

public class TimestampedObject
{

    private final Instant createdAt = SC2Pulse.instant();

    public Instant getCreatedAt()
    {
        return createdAt;
    }

}
