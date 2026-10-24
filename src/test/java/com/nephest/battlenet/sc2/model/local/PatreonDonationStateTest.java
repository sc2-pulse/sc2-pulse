// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class PatreonDonationStateTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new PatreonDonationState
            (
                "member-1",
                "USD",
                new BigDecimal("10")
            ),
            new PatreonDonationState
            (
                "member-1",
                "EUR",
                new BigDecimal("20")
            ),
            new PatreonDonationState
            (
                "member-2",
                "USD",
                new BigDecimal("10")
            )
        );
    }

}
