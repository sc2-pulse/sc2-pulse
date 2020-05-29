// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public final class TestUtil
{

    private TestUtil(){}

    public static void testUniqueness(Object object, Object equalObject, Object... notEqualObjects)
    {
        assertEquals(object, equalObject);
        assertEquals(object.hashCode(), equalObject.hashCode());
        assertEquals(object.toString(), equalObject.toString());

        for (Object notEqualObject : notEqualObjects)
        {
            assertNotEquals(object, notEqualObject);
            assertNotEquals(object.hashCode(), notEqualObject.hashCode());
            assertNotEquals(object.toString(), notEqualObject.toString());
        }
    }

}
