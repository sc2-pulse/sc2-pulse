// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TeamLegacyIdEntryTest
{

    public static Stream<Arguments> testToLegacyIdSectionString()
    {
        return Stream.of
        (
            Arguments.of(new TeamLegacyIdEntry(1, 234L, Race.TERRAN), "1.234.1"),
            Arguments.of(new TeamLegacyIdEntry(1, 234L), "1.234."),
            Arguments.of(new TeamLegacyIdEntry(1, 234L, true), "1.234.*"),
            Arguments.of(new TeamLegacyIdEntry(1, 234L, false), "1.234.")
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testToLegacyIdSectionString(TeamLegacyIdEntry entry, String expected)
    {
        assertEquals(expected, entry.toLegacyIdSectionString());
        assertEquals(expected, entry.appendLegacyIdSectionStringTo(new StringBuilder()).toString());
    }

    @MethodSource("testToLegacyIdSectionString")
    @ParameterizedTest
    public void testFromLegacyIdSectionString(TeamLegacyIdEntry expected, String str)
    {
        assertEquals(expected, TeamLegacyIdEntry.fromLegacyIdSectionString(str));
    }

    public static Stream<Arguments> whenFromLegacyIdSectionTextInvalidString_thenThrowException()
    {
        return Stream.of
        (
            Arguments.of("123", IllegalArgumentException.class, "2-3 sections expected"),
            Arguments.of("1.2.3.4", IllegalArgumentException.class, "2-3 sections expected"),

            Arguments.of("a.2.3", NumberFormatException.class, ""),
            Arguments.of("1.a.3", NumberFormatException.class, ""),
            Arguments.of("1.2.a", IllegalArgumentException.class, "Invalid id")
        );
    }

    @MethodSource
    @ParameterizedTest
    public void whenFromLegacyIdSectionTextInvalidString_thenThrowException
    (
        String in,
        Class<? extends Throwable> clazz,
        String msg
    )
    {
        assertThrows(clazz, ()->TeamLegacyIdEntry.fromLegacyIdSectionString(in), msg);
    }

    public static Stream<Arguments> testCompareTo()
    {
        return Stream.of
        (
            Arguments.of
            (
                new TeamLegacyIdEntry(1, 2L, Race.TERRAN),
                new TeamLegacyIdEntry(1, 2L),
                0
            ),
            Arguments.of
            (
                new TeamLegacyIdEntry(2, 1L),
                new TeamLegacyIdEntry(1, 2L),
                1
            ),
            Arguments.of
            (
                new TeamLegacyIdEntry(1, 1L),
                new TeamLegacyIdEntry(1, 2L),
                -1
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testCompareTo(TeamLegacyIdEntry a, TeamLegacyIdEntry b, int expected)
    {
        assertEquals(expected, a.compareTo(b));
    }

    public static Stream<Arguments> testUniqueness()
    {
        return Stream.of
        (
            Arguments.of(new TeamLegacyIdEntry(1, 2L, Race.TERRAN)),
            Arguments.of(new TeamLegacyIdEntry(1, 2L, true)),
            Arguments.of(new TeamLegacyIdEntry(1, 2L, false))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testUniqueness(TeamLegacyIdEntry entry)
    {
        TestUtil.testUniqueness
        (
            entry,
            new TeamLegacyIdEntry(entry.realm(), entry.id()),
            (Object[]) new TeamLegacyIdEntry[]
            {
                new TeamLegacyIdEntry(entry.realm() + 1, entry.id()),
                new TeamLegacyIdEntry(entry.realm(), entry.id() + 1)
            }
        );
    }

}
