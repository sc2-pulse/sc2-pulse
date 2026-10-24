// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Identifiable;

public enum DonationPlatform
implements Identifiable
{

    KO_FI(1),
    PATREON(2);

    private final int id;

    private DonationPlatform(int id)
    {
        this.id = id;
    }

    public static DonationPlatform fromId(int id)
    {
        for(DonationPlatform platform : DonationPlatform.values())
            if(platform.getId() == id) return platform;

        throw new IllegalArgumentException("Invalid id: " + id);
    }

    @Override
    public int getId()
    {
        return id;
    }

}
