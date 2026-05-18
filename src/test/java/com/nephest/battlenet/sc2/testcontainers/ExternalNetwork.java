// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.testcontainers;

import org.testcontainers.containers.Network;

public class ExternalNetwork
implements Network
{

    private final String id;

    public ExternalNetwork(String id)
    {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void close()
    {
    }

}
