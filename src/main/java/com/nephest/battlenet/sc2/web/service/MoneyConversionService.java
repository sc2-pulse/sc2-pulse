// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.GenericMoney;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MoneyConversionService
{

    private static final Logger LOG = LoggerFactory.getLogger
    (
        MoneyConversionService.class
    );

    private final MonetaryAmountConverter converter;

    public MoneyConversionService(MonetaryAmountConverter converter)
    {
        this.converter = converter;
    }

    /**
     * @param source source money
     * @param targetCurrency target currency code
     * @param time time of the exchange rate
     * @return converted money, money with null amount if conversion failed
     */
    public GenericMoney convert
    (
        GenericMoney source,
        String targetCurrency,
        OffsetDateTime time
    )
    {
        if(source.currency().equals(targetCurrency)) return source;

        try
        {
            return GenericMoney.from(converter.convert(
                source.toMonetaryAmount(),
                targetCurrency,
                time
            ));
        }
        catch (RuntimeException ex)
        {
            LOG.warn
            (
                "Unable to convert money from {} to {} at {}",
                source.currency(),
                targetCurrency,
                time,
                ex
            );
            return new GenericMoney(null, targetCurrency);
        }
    }

}
