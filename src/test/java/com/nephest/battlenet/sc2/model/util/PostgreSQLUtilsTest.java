// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PostgreSQLUtilsTest
{

    @Test
    public void testEscapeLike()
    {
        assertEquals("\\\\asd\\%\\\\\\%\\_", PostgreSQLUtils.escapeLikePattern("\\asd%\\%_"));
    }

}
