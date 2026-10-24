// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.Donation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import com.nephest.battlenet.sc2.model.local.GenericMoney;
import com.nephest.battlenet.sc2.model.local.PlatformDonationRef;
import com.nephest.battlenet.sc2.model.local.dao.DonationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty
(
    name = "com.nephest.battlenet.sc2.donation.target-currency"
)
public class DonationService
{

    private static final Logger LOG = LoggerFactory
        .getLogger(DonationService.class);
    private static final String UNIQUE_PLATFORM_DONATION_ID_CONSTRAINT =
        "uq_donation_platform_donation_id";

    private final DonationRepository donationRepository;
    private final MoneyConversionService moneyConversionService;
    private final String targetCurrency;

    public DonationService
    (
        DonationRepository donationRepository,
        MoneyConversionService moneyConversionService,
        @Value("${com.nephest.battlenet.sc2.donation.target-currency}")
        String targetCurrency
    )
    {
        this.donationRepository = donationRepository;
        this.moneyConversionService = moneyConversionService;
        this.targetCurrency = targetCurrency;
    }

    public String getTargetCurrency()
    {
        return targetCurrency;
    }

    public Donation create
    (
        DonationPlatform platform,
        String donationId,
        String donorId,
        String donorName,
        BigDecimal amount,
        String currency,
        OffsetDateTime created
    )
    {
        GenericMoney original = new GenericMoney(amount, currency);
        return new Donation
        (
            new PlatformDonationRef(platform, donationId, donorId, donorName),
            original,
            moneyConversionService.convert(original, targetCurrency, created),
            created
        );
    }

    public boolean save(Donation donation)
    {
        PlatformDonationRef ref = donation.getPlatformRef();
        if
        (
            donationRepository
                .existsByPlatformRefPlatformAndPlatformRefDonationId
                (
                    ref.platform(),
                    ref.donationId()
                )
        )
        {
            logDuplicate(ref);
            return false;
        }

        try
        {
            donationRepository.saveAndFlush(donation);
            return true;
        }
        catch (DataIntegrityViolationException ex)
        {
            if(!isDuplicate(ex)) throw ex;

            logDuplicate(ref);
            return false;
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException ex)
    {
        for(Throwable t = ex; t != null; t = t.getCause())
            if
            (
                t instanceof ConstraintViolationException cve
                    && UNIQUE_PLATFORM_DONATION_ID_CONSTRAINT
                        .equalsIgnoreCase(cve.getConstraintName())
            )
                return true;
        return false;
    }

    private void logDuplicate(PlatformDonationRef ref)
    {
        LOG.warn
        (
            "Skipping duplicate donation: platform={}, donationId={}",
            ref.platform(),
            ref.donationId()
        );
    }

}
