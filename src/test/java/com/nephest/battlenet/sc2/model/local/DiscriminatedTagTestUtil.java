// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.model.util.DiscriminatedTag;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DiscriminatedTagTestUtil
{

    public static void testTagAndDiscriminator
    (
        Consumer<String> nameSetter,
        Supplier<DiscriminatedTag> tagGetter
    )
    {
        assertNull(tagGetter.get().tag());
        assertNull(tagGetter.get().discriminator());

        nameSetter.accept("Name1#123");
        assertEquals("Name1", tagGetter.get().tag());
        assertEquals(123, tagGetter.get().discriminator());

        nameSetter.accept("Name2#456");
        assertEquals("Name2", tagGetter.get().tag());
        assertEquals(456, tagGetter.get().discriminator());
    }

    public static void whenFakeName_thenTagAndDiscriminatorAreNull
    (
        Consumer<String> nameSetter,
        Supplier<DiscriminatedTag> tagGetter
    )
    {
        nameSetter.accept("f#123");
        assertNull(tagGetter.get().tag());
        assertNull(tagGetter.get().discriminator());
    }

    public static void whenFakeDiscriminator_thenDiscriminatorIsNull
    (
        Consumer<String> nameSetter,
        Supplier<DiscriminatedTag> tagGetter
    )
    {
        nameSetter.accept("Name#1");
        assertEquals("Name", tagGetter.get().tag());
        assertNull(tagGetter.get().discriminator());
    }

}
