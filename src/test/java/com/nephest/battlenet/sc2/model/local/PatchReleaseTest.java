// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class PatchReleaseTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = SC2Pulse.offsetDateTime();
        TestUtil.testUniqueness
        (
            new PatchRelease(1, Region.US, equalOdt),
            new PatchRelease(1, Region.US, equalOdt.minusSeconds(1)),

            new PatchRelease(2, Region.US, equalOdt),
            new PatchRelease(1, Region.EU, equalOdt)
        );
    }

}
