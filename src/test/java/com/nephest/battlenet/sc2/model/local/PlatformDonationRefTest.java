// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class PlatformDonationRefTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new PlatformDonationRef
            (
                DonationPlatform.KO_FI,
                "donation-1",
                "donor-1",
                "Donor 1"
            ),
            new PlatformDonationRef
            (
                DonationPlatform.KO_FI,
                "donation-1",
                "donor-2",
                "Donor 2"
            ),
            new PlatformDonationRef
            (
                DonationPlatform.PATREON,
                "donation-1",
                "donor-1",
                "Donor 1"
            ),
            new PlatformDonationRef
            (
                DonationPlatform.KO_FI,
                "donation-2",
                "donor-1",
                "Donor 1"
            )
        );
    }

}
