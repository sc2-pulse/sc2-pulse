// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlizzardAccessTokenTest
{

    @Test
    public void testValidity()
    {
        BlizzardAccessToken token = new BlizzardAccessToken("sad", "sada", 3600);
        assertTrue(token.isValid());
        assertFalse(token.isExpired());
        token.setExpiresIn(-1);
        assertFalse(token.isValid());
        assertTrue(token.isExpired());
    }

}
