// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;

public class CursorUtilTest
{

    public static Stream<Arguments> cursorNavigableQueryFormatArguments()
    {
        return Stream.of
        (
            Arguments.of
            (
                "%1$s %2$s",
                Sort.Direction.ASC,
                ScrollPosition.Direction.FORWARD,
                true,
                new String[0],
                "ASC >"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                Sort.Direction.ASC,
                ScrollPosition.Direction.BACKWARD,
                true,
                new String[0],
                "DESC <"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                Sort.Direction.DESC,
                ScrollPosition.Direction.FORWARD,
                true,
                new String[0],
                "DESC <"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                Sort.Direction.DESC,
                ScrollPosition.Direction.BACKWARD,
                true,
                new String[0],
                "ASC >"
            ),
            Arguments.of
            (
                "%1$s %2$s",
                Sort.Direction.ASC,
                ScrollPosition.Direction.FORWARD,
                false,
                new String[0],
                "> ASC"
            ),
            Arguments.of
            (
                "%1$s %2$s %3$s %4$s",
                Sort.Direction.ASC,
                ScrollPosition.Direction.FORWARD,
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
        Sort.Direction order,
        ScrollPosition.Direction direction,
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
        Sort.Direction order,
        ScrollPosition.Direction direction,
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
