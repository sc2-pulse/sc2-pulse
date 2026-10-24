// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import javax.money.MonetaryAmount;
import org.javamoney.moneta.Money;

@Embeddable
public record GenericMoney
(
    BigDecimal amount,
    @NotNull
    @Column(nullable = false)
    String currency
)
{

    public static GenericMoney from(MonetaryAmount money)
    {
        return new GenericMoney
        (
            money.getNumber().numberValueExact(BigDecimal.class),
            money.getCurrency().getCurrencyCode()
        );
    }

    @Override
    public String toString()
    {
        return "GenericMoney{"
            + "amount=" + amount
            + ", currency='" + currency
        + "'}";
    }

    public MonetaryAmount toMonetaryAmount()
    {
        return Money.of(amount(), currency());
    }

}
