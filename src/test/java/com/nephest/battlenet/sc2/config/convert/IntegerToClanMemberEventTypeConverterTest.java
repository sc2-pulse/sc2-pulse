// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class IntegerToClanMemberEventTypeConverterTest
{

    private final IntegerToClanMemberEventTypeConverter converter
        = new IntegerToClanMemberEventTypeConverter();

    @CsvSource
    ({
        "0, LEAVE",
        "1, JOIN"
    })
    @ParameterizedTest
    public void testConvert(Integer input, ClanMemberEvent.EventType expectedResult)
    {
        assertEquals(expectedResult, converter.convert(input));
    }

}
