// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StringToSortParameterConverterTest
{

    private StringToSortParameterConverter converter;

    @BeforeEach
    void setUp()
    {
        converter = new StringToSortParameterConverter();
    }

    @Test
    void shouldConvertSimpleNameToAsc()
    {
        SortParameter param = converter.convert("name");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.ASC, param.order());
    }

    @Test
    void shouldConvertMinusPrefixToDesc()
    {
        SortParameter param = converter.convert("-name");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.DESC, param.order());
    }

    @Test
    void shouldConvertPlusPrefixToAsc()
    {
        SortParameter param = converter.convert("+name");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.ASC, param.order());
    }

    @Test
    void shouldConvertColonAscToAsc()
    {
        SortParameter param = converter.convert("name:asc");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.ASC, param.order());
    }

    @Test
    void shouldConvertColonDescToDesc()
    {
        SortParameter param = converter.convert("name:desc");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.DESC, param.order());
    }

    @Test
    void shouldTrimWhitespace()
    {
        SortParameter param = converter.convert("   -name   ");
        assertEquals("name", param.field());
        assertEquals(SortingOrder.DESC, param.order());
    }

    @Test
    void shouldThrowOnEmptyString()
    {
        assertThrows(IllegalArgumentException.class, ()->converter.convert("  "));
    }

    @Test
    void shouldThrowOnInvalidSuffix()
    {
        assertThrows(IllegalArgumentException.class, ()->converter.convert("name:wrong"));
    }

}

