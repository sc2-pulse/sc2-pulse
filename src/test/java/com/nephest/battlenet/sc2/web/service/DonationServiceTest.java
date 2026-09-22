// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.Donation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import com.nephest.battlenet.sc2.model.local.GenericMoney;
import com.nephest.battlenet.sc2.model.local.PlatformDonationRef;
import com.nephest.battlenet.sc2.model.local.dao.DonationRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
public class DonationServiceTest
{

    private static final Donation DONATION = new Donation
    (
        new PlatformDonationRef
        (
            DonationPlatform.KO_FI,
            "transaction-1",
            "donor-1",
            "Donor"
        ),
        new GenericMoney(BigDecimal.ONE, "USD"),
        new GenericMoney(BigDecimal.ONE, "USD"),
        OffsetDateTime.parse("2026-09-16T10:00:00Z")
    );

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private MoneyConversionService moneyConversionService;

    private DonationService service;

    @BeforeEach
    public void beforeEach()
    {
        service = new DonationService
        (
            donationRepository,
            moneyConversionService,
            "USD"
        );
    }

    @Test
    public void whenUniqueDonationIdIsViolated_thenSkipDuplicate()
    {
        when(donationRepository.saveAndFlush(any()))
            .thenThrow(integrityViolation("uq_donation_platform_donation_id"));

        assertFalse(service.save(DONATION));
    }

    @Test
    public void whenOtherIntegrityConstraintIsViolated_thenRethrow()
    {
        DataIntegrityViolationException ex = integrityViolation("other");
        when(donationRepository.saveAndFlush(any())).thenThrow(ex);

        assertSame
        (
            ex,
            assertThrows
            (
                DataIntegrityViolationException.class,
                ()->service.save(DONATION)
            )
        );
    }

    private static DataIntegrityViolationException integrityViolation
    (
        String constraintName
    )
    {
        return new DataIntegrityViolationException
        (
            "test",
            new ConstraintViolationException
            (
                "test",
                new SQLException("test"),
                constraintName
            )
        );
    }

}
