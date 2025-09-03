// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class CursorToPositionStringConverterTest
{

    CursorToPositionStringConverter converter
        = new CursorToPositionStringConverter(TestUtil.OBJECT_MAPPER);

    @MethodSource
    (
        "com.nephest.battlenet.sc2.model.navigation"
            + ".CursorUtilTest#positionEncodingArguments"
    )
    @ParameterizedTest
    public void testConvert(Position position, String token)
    {
        assertEquals(token, converter.convert(new Cursor(position, NavigationDirection.FORWARD)));
    }

}
