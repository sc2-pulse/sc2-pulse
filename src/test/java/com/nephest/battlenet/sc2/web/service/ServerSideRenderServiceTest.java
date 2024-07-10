// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class ServerSideRenderServiceTest
{

    private final ServerSideRenderService service = new ServerSideRenderService();

    @Test
    public void testReverseOrder()
    {
        Integer[] unordered = new Integer[]{1, null, 2, 3};
        assertArrayEquals(new Integer[]{3, 2, 1, null}, service.reverseOrder(unordered));
    }

}
