// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class AuthenticationRequestTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = OffsetDateTime.now();
        TestUtil.testUniqueness
        (
            new AuthenticationRequest("1", equalOdt),
            new AuthenticationRequest("1", equalOdt.minusDays(1)),

            new AuthenticationRequest("2", equalOdt)
        );
    }

}
