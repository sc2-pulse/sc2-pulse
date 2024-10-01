// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.Optional;

public interface LinkedAccountSupplier
{

    SocialMedia getSocialMedia();

    Optional<?> getAccountByPulseAccountId(long pulseAccountId);

}
