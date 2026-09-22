// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class GenericMoneyTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new GenericMoney(new BigDecimal("1.25"), "USD"),
            new GenericMoney(new BigDecimal("1.25"), "USD"),

            new GenericMoney(new BigDecimal("1.26"), "USD"),
            new GenericMoney(new BigDecimal("1.25"), "EUR")
        );
    }

    @Test
    public void testMonetaryAmountConversion()
    {
        GenericMoney money = new GenericMoney
        (
            new BigDecimal("1.23456789"),
            "USD"
        );

        assertEquals(money, GenericMoney.from(money.toMonetaryAmount()));
    }

}
