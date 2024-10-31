// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import org.junit.jupiter.api.Test;

public class IdentifiableToMinimalObjectConverterTest
{

    private final IdentifiableToMinimalObjectConverter converter
        = new IdentifiableToMinimalObjectConverter();

    @Test
    public void testConvert()
    {
        assertEquals(3, converter.convert(Region.KR));
        assertEquals(201, converter.convert(QueueType.LOTV_1V1));
    }

}
