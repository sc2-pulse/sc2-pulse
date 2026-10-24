// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.IntStream;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.MonetaryConversions;
import org.springframework.stereotype.Service;

/**
 * Converts money using ECB historic reference rates.
 */
@Service
public class ReferenceMonetaryAmountConverter
implements MonetaryAmountConverter
{

    /**
     * ECB reference rates since 1999.
     */
    public static final String RATE_PROVIDER = "ECB-HIST";

    /**
     * ECB doesn't publish rates on weekends and TARGET holidays, the most
     * recent previous rate is used instead.
     */
    public static final int MAX_RATE_AGE_DAYS = 7;

    /**
     * ECB rate dates are ECB(Frankfurt) local dates.
     */
    public static final ZoneId RATE_ZONE = ZoneId.of("Europe/Berlin");

    @Override
    public MonetaryAmount convert
    (
        MonetaryAmount source,
        String targetCurrency,
        OffsetDateTime time
    )
    {
        LocalDate date = time.atZoneSameInstant(RATE_ZONE).toLocalDate();
        /*
            The rate is requested from the provider directly because
            MonetaryConversions.getConversion(query) ignores query dates and
            uses the most recent rate.
         */
        ExchangeRate rate = MonetaryConversions
            .getExchangeRateProvider(RATE_PROVIDER)
            .getExchangeRate
            (
                ConversionQueryBuilder.of()
                    .setBaseCurrency(source.getCurrency())
                    .setTermCurrency(targetCurrency)
                    .set(LocalDate[].class, getRateDates(date))
                    .build()
            );
        return source.multiply(rate.getFactor())
            .getFactory()
            .setCurrency(rate.getCurrency())
            .create();
    }

    /**
     * @param date target date
     * @return target date followed by previous dates, most recent first
     */
    private static LocalDate[] getRateDates(LocalDate date)
    {
        return IntStream.range(0, MAX_RATE_AGE_DAYS)
            .mapToObj(date::minusDays)
            .toArray(LocalDate[]::new);
    }

}
