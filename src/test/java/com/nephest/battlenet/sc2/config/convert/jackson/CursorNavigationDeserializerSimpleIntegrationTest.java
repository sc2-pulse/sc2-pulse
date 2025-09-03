// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtilTest;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CursorNavigationDeserializerSimpleIntegrationTest
{

    private static final List<Position> POSITIONS =  CursorUtilTest.positionEncodingArguments()
        .map(a->(Position) a.get()[0])
        .toList();
    private static final List<String> TOKENS =  CursorUtilTest.positionEncodingArguments()
        .map(a->(String) a.get()[1])
        .toList();

    public static Stream<Arguments> testDeserialize()
    {
        return Stream.of
        (
            Arguments.of
            (
                "{\"after\": \"" + TOKENS.get(1) + "\""
                    + ", \"before\": \"" + TOKENS.get(0) + "\"}",
                new CursorNavigation
                (
                    new Cursor(POSITIONS.get(0), NavigationDirection.BACKWARD),
                    new Cursor(POSITIONS.get(1), NavigationDirection.FORWARD)
                )
            ),
            Arguments.of
            (
                "{\"after\": \"" + TOKENS.get(1) + "\""
                    + ", \"before\": null}",
                new CursorNavigation
                (
                    null,
                    new Cursor(POSITIONS.get(1), NavigationDirection.FORWARD)
                )
            ),
            Arguments.of
            (
                "{\"after\": null"
                    + ", \"before\": \"" + TOKENS.get(0) + "\"}",
                new CursorNavigation
                (
                    new Cursor(POSITIONS.get(0), NavigationDirection.BACKWARD),
                    null
                )
            ),
            Arguments.of
            (
                "{\"after\": null, \"before\": null}",
                new CursorNavigation(null, null)
            ),
            Arguments.of("null", null)
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testDeserialize(String json, CursorNavigation expected)
    throws IOException
    {
        String dtoJson = "{\"navigation\": " + json + "}";
        TestDto dto = TestUtil.OBJECT_MAPPER.readValue(dtoJson, TestDto.class);
        assertEquals(expected, dto.navigation());
    }

    private record TestDto(@JsonDeserialize(using = CursorNavigationDeserializer.class) CursorNavigation navigation) {}

}
