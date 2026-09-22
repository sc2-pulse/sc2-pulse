// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import java.time.OffsetDateTime;
import javax.money.MonetaryAmount;

public interface MonetaryAmountConverter
{

    /**
     * @param source source money
     * @param targetCurrency target currency code
     * @param time time of the exchange rate
     * @return converted money
     */
    MonetaryAmount convert
    (
        MonetaryAmount source,
        String targetCurrency,
        OffsetDateTime time
    );

}
