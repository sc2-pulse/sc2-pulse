// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.web.service.PatreonDonationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Hidden
@RestController
@ConditionalOnProperty
(
    name = "com.nephest.battlenet.sc2.donation.webhook.patreon.secret"
)
public class PatreonWebhookController
{

    private final PatreonDonationService patreonDonationService;

    public PatreonWebhookController(PatreonDonationService service)
    {
        this.patreonDonationService = service;
    }

    @PostMapping
    (
        value = "/webhooks/patreon",
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public void accept
    (
        @RequestHeader("X-Patreon-Event") String event,
        @RequestBody byte[] body
    )
    {
        try
        {
            patreonDonationService.process(event, body);
        }
        catch (IllegalArgumentException | ConstraintViolationException ex)
        {
            throw new ResponseStatusException
            (
                HttpStatus.BAD_REQUEST,
                "Invalid Patreon donation",
                ex
            );
        }
    }

}
