// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.kofi.KoFiDonation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.DonationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@ConditionalOnProperty
(
    name = "com.nephest.battlenet.sc2.donation.webhook.ko-fi.verification-token"
)
public class KoFiWebhookController
{

    private static final Logger LOG = LoggerFactory
        .getLogger(KoFiWebhookController.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final DonationService donationService;

    public KoFiWebhookController
    (
        ObjectMapper objectMapper,
        Validator validator,
        DonationService donationService
    )
    {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.donationService = donationService;
    }

    @PostMapping
    (
        value = "/webhooks/ko-fi",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public void accept(@RequestParam("data") String data)
    {
        KoFiDonation koFiDonation = read(data);
        if(!koFiDonation.isDonation())
        {
            LOG.debug("Ignoring Ko-fi payment: {}", koFiDonation);
            return;
        }

        validate(koFiDonation);
        donationService.save(donationService.create
        (
            DonationPlatform.KO_FI,
            koFiDonation.donationId(),
            koFiDonation.email(),
            koFiDonation.fromName(),
            koFiDonation.amount(),
            koFiDonation.currency(),
            koFiDonation.timestamp() != null
                ? SC2Pulse.offsetDateTime(koFiDonation.timestamp())
                : SC2Pulse.offsetDateTime()
        ));
    }

    private KoFiDonation read(String data)
    {
        try
        {
            return objectMapper.readValue(data, KoFiDonation.class);
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalArgumentException("Invalid Ko-fi payload", ex);
        }
    }

    private void validate(KoFiDonation koFiDonation)
    {
        var violations = validator.validate(koFiDonation);
        if(!violations.isEmpty())
            throw new ConstraintViolationException(violations);
    }

}
