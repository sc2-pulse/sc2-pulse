// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.PatreonDonationState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatreonDonationStateRepository
extends JpaRepository<PatreonDonationState, String>
{

}
