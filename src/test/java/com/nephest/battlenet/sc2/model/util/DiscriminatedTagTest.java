// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DiscriminatedTagTest
{

    @Test
    public void testParse()
    {
        DiscriminatedTag tag = DiscriminatedTag.parse("tag_123", "_", i->i+1);
        assertEquals("tag", tag.tag());
        assertEquals(124, tag.discriminator());
        assertEquals("tag_124", tag.toString("_"));
    }

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new DiscriminatedTag("tag1", 1),
            new DiscriminatedTag("tag1", 1),

            new DiscriminatedTag("tag2", 1),
            new DiscriminatedTag("tag1", 2),
            new DiscriminatedTag(null, null)
        );
    }

    public static Stream<Arguments> testCompareTo()
    {
        return Stream.of
        (
            Arguments.of(new DiscriminatedTag("tag1", 1), new DiscriminatedTag("tag1", 1), 0),

            Arguments.of(new DiscriminatedTag("tag2", 1), new DiscriminatedTag("tag1", 1), 1),
            Arguments.of(new DiscriminatedTag("tag1", 2), new DiscriminatedTag("tag1", 1), 1),

            Arguments.of(new DiscriminatedTag("tag0", 1), new DiscriminatedTag("tag1", 1), -1),
            Arguments.of(new DiscriminatedTag("tag1", 0), new DiscriminatedTag("tag1", 1), -1),

            Arguments.of(new DiscriminatedTag(null, null), new DiscriminatedTag("tag1", 1), 1)
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testCompareTo(DiscriminatedTag a, DiscriminatedTag b, int expected)
    {
        assertEquals(expected, a.compareTo(b));
    }

}
