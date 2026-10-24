// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.time.OffsetDateTime;

public interface AnonymousDonation
{

    Integer getId();
    AnonymousPlatformDonationRef getPlatformRef();
    GenericMoney getMoneyTarget();
    OffsetDateTime getCreated();

    public interface AnonymousPlatformDonationRef
    {

        DonationPlatform getPlatform();

    }

}
