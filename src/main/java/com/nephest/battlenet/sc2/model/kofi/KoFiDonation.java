// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.kofi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Ko-fi webhook data. Only the fields used by SC2 Pulse are mapped.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KoFiDonation
(
    String verificationToken,
    @NotBlank String messageId,
    String kofiTransactionId,
    OffsetDateTime timestamp,
    String type,
    String fromName,
    String email,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String currency
)
{

    /**
     * Payment types that are donations. Other types, such as
     * {@code Shop Order} and {@code Commission}, are purchases.
     */
    public static final Set<String> DONATION_TYPES = Set.of
    (
        "Donation",
        "Subscription"
    );

    public boolean isDonation()
    {
        return DONATION_TYPES.contains(type);
    }

    /**
     * @return Ko-fi transaction id if present, message id otherwise
     */
    public String donationId()
    {
        return kofiTransactionId == null || kofiTransactionId.isBlank()
            ? messageId
            : kofiTransactionId;
    }

    /**
     * Excludes the verification token and donor's email.
     */
    @Override
    public String toString()
    {
        return "KoFiDonation{"
            + "messageId='" + messageId
            + "', kofiTransactionId='" + kofiTransactionId
            + "', timestamp=" + timestamp
            + ", type='" + type
            + "', amount=" + amount
            + ", currency='" + currency
        + "'}";
    }

}
