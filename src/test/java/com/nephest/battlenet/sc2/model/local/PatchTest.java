// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class PatchTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = OffsetDateTime.now();
        TestUtil.testUniqueness
        (
            new Patch(1L, "1.1.1", equalOdt),
            new Patch(1L, "2.2.2", equalOdt.minusSeconds(1)),
            new Patch(2L, "1.1.1", equalOdt)
        );
    }

}
