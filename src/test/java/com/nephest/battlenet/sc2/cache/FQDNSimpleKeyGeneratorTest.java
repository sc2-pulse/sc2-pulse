// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FQDNSimpleKeyGeneratorTest
{

    private FQDNSimpleKeyGenerator keyGenerator;

    @BeforeEach
    public void beforeEach()
    {
        keyGenerator = new FQDNSimpleKeyGenerator();
    }

    @Test
    public void whenSameFqdn_thenKeysAreEqual()
    throws NoSuchMethodException
    {
        assertEquals
        (
            keyGenerator.generate(this, Object.class.getMethod("equals", Object.class), "param"),
            keyGenerator.generate(this, Object.class.getMethod("equals", Object.class), "param")
        );
    }

    @Test
    public void whenDifferentObjects_thenKeysAreNotEqual()
    throws NoSuchMethodException
    {
        Object obj = new Object();
        assertNotEquals
        (
            keyGenerator.generate(this, Object.class.getMethod("equals", Object.class), "param"),
            keyGenerator.generate(obj, Object.class.getMethod("equals", Object.class), "param")
        );
    }

    @Test
    public void whenDifferentMethods_thenKeysAreNotEqual()
    throws NoSuchMethodException
    {
        assertNotEquals
        (
            keyGenerator.generate(this, Object.class.getMethod("equals", Object.class), "param"),
            keyGenerator.generate(this, Object.class.getMethod("hashCode"), "param")
        );
    }

}
