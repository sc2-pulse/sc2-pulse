// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CursorUtilTest
{

    public static Stream<Arguments> positionEncodingArguments()
    {
        return Stream.of
        (
            Arguments.of
            (
                new Position(1L, List.of("123", 345)),
                "eyJ2IjoxLCJhIjpbIjEyMyIsMzQ1XX0"
            ),
            Arguments.of
            (
                new Position(2L, List.of("345", 123)),
                "eyJ2IjoyLCJhIjpbIjM0NSIsMTIzXX0"
            ),
            Arguments.of(null, null)
        );
    }

    @MethodSource("positionEncodingArguments")
    @ParameterizedTest
    public void testEncodePosition(Position position, String token)
    {
        assertEquals(token, CursorUtil.encodePosition(position, TestUtil.OBJECT_MAPPER));
    }

    @MethodSource("positionEncodingArguments")
    @ParameterizedTest
    public void testDecodePosition(Position position, String token)
    {
        assertEquals(position, CursorUtil.decodePosition(token, TestUtil.OBJECT_MAPPER));
    }

    @Test
    public void shouldThrowOnInvalidToken()
    {
        String invalidToken = "invalidToken";
        assertThrows
        (
            IllegalArgumentException.class,
            ()->CursorUtil.decodePosition(invalidToken, TestUtil.OBJECT_MAPPER),
            "Invalid cursor position token: " + invalidToken
        );
    }

    public static Stream<Arguments> cursorNavigableQueryFormatArguments()
    {
        return Stream.of
        (
            Arguments.of
            (
                "%1$s %2$s",
                SortingOrder.ASC,
                NavigationDirection.FORWARD,
                true,
                new String[0],
                "ASC >"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                SortingOrder.ASC,
                NavigationDirection.BACKWARD,
                true,
                new String[0],
                "DESC <"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                SortingOrder.DESC,
                NavigationDirection.FORWARD,
                true,
                new String[0],
                "DESC <"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                SortingOrder.DESC,
                NavigationDirection.BACKWARD,
                true,
                new String[0],
                "ASC >"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                SortingOrder.ASC,
                NavigationDirection.FORWARD,
                false,
                new String[0],
                "> ASC"
            ),
            Arguments.of
            (
                "%1$s %2$s %3$s %4$s",
                SortingOrder.ASC,
                NavigationDirection.FORWARD,
                true,
                new String[]{"a", "b"},
                "ASC > a b"
            )
        );
    }

    @MethodSource("cursorNavigableQueryFormatArguments")
    @ParameterizedTest
    public void testGetCursorNavigableQueryFormatArguments
    (
        String template,
        SortingOrder order,
        NavigationDirection direction,
        boolean orderFirst,
        String[] additionalArguments,
        String expected
    )
    {
        assertArrayEquals
        (
            expected.split(" "),
            CursorUtil.getCursorNavigableQueryFormatArguments
            (
                order,
                direction,
                orderFirst,
                additionalArguments
            )
        );
    }

    @MethodSource("cursorNavigableQueryFormatArguments")
    @ParameterizedTest
    public void testFormatCursorNavigableQuery
    (
        String template,
        SortingOrder order,
        NavigationDirection direction,
        boolean orderFirst,
        String[] additionalArguments,
        String expected
    )
    {
        assertEquals
        (
            expected,
            CursorUtil.formatCursorNavigableQuery
            (
                template,
                order,
                direction,
                orderFirst,
                additionalArguments
            )
        );
    }

}
