// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.GenericMoney;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MoneyConversionServiceTest
{

    private static final OffsetDateTime TIME
        = OffsetDateTime.parse("2023-02-17T12:00:00Z");

    @Mock
    private MonetaryAmountConverter converter;

    @InjectMocks
    private MoneyConversionService service;

    @Test
    public void shouldNotRoundIdentityConversion()
    {
        BigDecimal amount = new BigDecimal("1.234567890123456789");
        GenericMoney result = service.convert
        (
            new GenericMoney(amount, "USD"),
            "USD",
            TIME
        );

        assertEquals(amount, result.amount());
        assertEquals("USD", result.currency());
        verifyNoInteractions(converter);
    }

    @Test
    public void shouldConvertToOtherCurrencyWithoutRounding()
    {
        GenericMoney expected
             = new GenericMoney(new BigDecimal("2.134567890123456789"), "EUR");
        when(converter.convert(any(), eq("EUR"), eq(TIME)))
            .thenReturn(expected.toMonetaryAmount());

        assertEquals
        (
            expected,
            service.convert
            (
                new GenericMoney(new BigDecimal("1.234567890123456789"), "USD"),
                "EUR",
                TIME
            )
        );
    }

}
