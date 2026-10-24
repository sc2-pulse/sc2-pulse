// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class DonationTest
{

    private static final OffsetDateTime CREATED = SC2Pulse.offsetDateTime();

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            donation
            (
                new PlatformDonationRef
                (
                    DonationPlatform.KO_FI,
                    "donation-1",
                    "donor-1",
                    "Donor 1"
                )
            ),
            new Donation
            (
                new PlatformDonationRef
                (
                    DonationPlatform.KO_FI,
                    "donation-1",

                    "donor-2",
                    "Donor 2"
                ),
                new GenericMoney(BigDecimal.TEN, "EUR"),
                new GenericMoney(BigDecimal.ONE, "USD"),
                CREATED.minusSeconds(1)
            ),
            donation(new PlatformDonationRef(
                DonationPlatform.PATREON,
                "donation-1",
                "donor-1",
                "Donor 1"
            )),
            donation(new PlatformDonationRef(
                DonationPlatform.KO_FI,
                "donation-2",
                "donor-1",
                "Donor 1"
            ))
        );
    }

    private static Donation donation(PlatformDonationRef ref)
    {
        return new Donation
        (
            ref,
            new GenericMoney(BigDecimal.ONE, "USD"),
            new GenericMoney(BigDecimal.TEN, "EUR"),
            CREATED
        );
    }

}
