// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.SortingOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SortParameterTest
{

    @CsvSource
    ({
        "name, ASC, name",
        "name, DESC, -name"
    })
    @ParameterizedTest
    void testToPrefixedString(String field, SortingOrder order, String expected)
    {
        assertEquals(expected, new SortParameter(field, order).toPrefixedString());
    }

    @CsvSource
    ({
        "name, ASC, name:asc",
        "name, DESC, name:desc"
    })
    @ParameterizedTest
    void testToSuffixedString(String field, SortingOrder order, String expected)
    {
        assertEquals(expected, new SortParameter(field, order).toSuffixedString());
    }

}

