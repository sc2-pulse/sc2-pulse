// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class KoFiWebhookVerificationFilterTest
{

    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @ParameterizedTest
    public void whenTokenIsNullOrBlank_thenThrowException(String token)
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->new KoFiWebhookVerificationFilter(TestUtil.OBJECT_MAPPER, token)
        );
    }

}
