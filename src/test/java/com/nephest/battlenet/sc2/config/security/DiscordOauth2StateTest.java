// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DiscordOauth2StateTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new DiscordOauth2State
            (
                new byte[]{0x01, 0x02},
                EnumSet.of(DiscordOauth2State.Flag.LINKED_ROLE)
            ),
            new DiscordOauth2State
            (
                new byte[]{0x01, 0x02},
                EnumSet.of(DiscordOauth2State.Flag.LINKED_ROLE)
            ),

            new DiscordOauth2State
            (
                new byte[]{0x02, 0x02},
                EnumSet.of(DiscordOauth2State.Flag.LINKED_ROLE)
            ),
            new DiscordOauth2State
            (
                new byte[]{0x01, 0x01},
                EnumSet.of(DiscordOauth2State.Flag.LINKED_ROLE)
            ),
            new DiscordOauth2State
            (
                new byte[]{0x01, 0x02},
                Set.of()
            )
        );
    }

    public static Stream<Arguments> testConversion()
    {
        return Stream.of
        (
            Arguments.of
            (
                new DiscordOauth2State
                (
                    new byte[]{0x01, 0x02},
                    EnumSet.of(DiscordOauth2State.Flag.LINKED_ROLE)
                ),
                "AQIB"
            ),
            Arguments.of
            (
                new DiscordOauth2State
                (
                    new byte[]{0x01, 0x02},
                    Set.of()
                ),
                "AQI="
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testConversion(DiscordOauth2State state, String uriString)
    {
        assertEquals(uriString, state.toUriString());
        assertEquals(state, DiscordOauth2State.fromUrlString(uriString, 2));
    }

    @Test
    public void testImmutability()
    {
        byte[] bytes = new byte[]{0x01, 0x02};
        Set<DiscordOauth2State.Flag> flags = EnumSet.allOf(DiscordOauth2State.Flag.class);
        DiscordOauth2State state = new DiscordOauth2State(bytes, flags);

        bytes[0] = 0x03;
        state.getIdCopy()[0] = 0x03;
        flags.clear();
        assertThrows(UnsupportedOperationException.class, ()->state.getFlags().clear());

        assertEquals(0x01, state.getIdCopy()[0]);
        assertEquals(EnumSet.allOf(DiscordOauth2State.Flag.class), state.getFlags());
    }

    public static Stream<Arguments> testFromUriStringCornerCases()
    {
        return Stream.of
        (
            Arguments.of
            (
                (Executable) ()->DiscordOauth2State.fromUrlString(null, 1),
                "State expected"
            ),
            Arguments.of
            (
                (Executable) ()->DiscordOauth2State.fromUrlString("", 1),
                "State expected"
            ),

            Arguments.of
            (
                (Executable) ()->DiscordOauth2State.fromUrlString("IDDQ", 0),
                "Positive idLength expected, got 0"
            ),
            Arguments.of
            (
                (Executable) ()->DiscordOauth2State.fromUrlString("IDDQ", -1),
                "Positive idLength expected, got -1"
            ),

            Arguments.of
            (
                (Executable) ()->DiscordOauth2State.fromUrlString("IDDQ", 999),
                "Decoded state is shorted than idLength"
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFromUriStringCornerCases(Executable cornerCase, String errorText)
    {
        assertThrows(IllegalArgumentException.class, cornerCase, errorText);
    }

}
