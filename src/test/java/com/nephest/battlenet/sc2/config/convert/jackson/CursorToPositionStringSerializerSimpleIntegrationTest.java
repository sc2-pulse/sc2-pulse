// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class CursorToPositionStringSerializerSimpleIntegrationTest
{

    @MethodSource
    (
        "com.nephest.battlenet.sc2.model.navigation"
            + ".CursorUtilTest#positionEncodingArguments"
    )
    @ParameterizedTest
    public void testConvert(Position position, String token)
    throws JsonProcessingException
    {
        TestDto dto = new TestDto(new Cursor(position, NavigationDirection.FORWARD));
        assertEquals
        (
            "{\"cursor\":" + (token == null ? "null" : "\"" + token + "\"") + "}",
            TestUtil.OBJECT_MAPPER.writeValueAsString(dto)
        );
    }

    private record TestDto
    (
        @JsonSerialize(using = CursorToPositionStringSerializer.class) Cursor cursor
    )
    {}

}
