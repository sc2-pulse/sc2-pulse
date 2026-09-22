// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.adapter.ScrollableRepository;
import com.nephest.battlenet.sc2.model.local.Donation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository
extends JpaRepository<Donation, Integer>,
ScrollableRepository
{

    boolean existsByPlatformRefPlatformAndPlatformRefDonationId
    (
        DonationPlatform platform,
        String donationId
    );

}
