// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.web.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.web.util.ConversionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ConversionUtilIT
{

    @Autowired
    private ConversionUtil conversionUtil;

    @Test
    public void testConvertToString()
    {
        assertEquals(SortingOrder.ASC.name(), conversionUtil.convertToString(SortingOrder.ASC));
    }

}
