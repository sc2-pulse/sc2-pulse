// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.patreon;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

/**
 * Patreon member resource. Only the fields used by SC2 Pulse are mapped.
 */
public record PatreonMember
(
    String id,
    Attributes attributes,
    Relationships relationships
)
{

    public static final String CAMPAIGN_TYPE = "campaign";

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Attributes
    (
        Long campaignLifetimeSupportCents,
        Long lifetimeSupportCents,
        String fullName,
        OffsetDateTime lastChargeDate,
        OffsetDateTime pledgeRelationshipStart
    )
    {

        /**
         * Patreon's member reference uses campaign_lifetime_support_cents,
         * while its webhook example still uses lifetime_support_cents.
         *
         * @return campaign lifetime support if present, legacy lifetime
         * support otherwise
         */
        public Long effectiveLifetimeSupportCents()
        {
            return campaignLifetimeSupportCents != null
                ? campaignLifetimeSupportCents
                : lifetimeSupportCents;
        }

        /**
         * Members that haven't paid anything, e.g. members that haven't been
         * charged yet, free trials, and gifted memberships, have no lifetime
         * support.
         *
         * @return true if the member has paid anything to the campaign
         */
        public boolean hasLifetimeSupport()
        {
            Long cents = effectiveLifetimeSupportCents();
            return cents != null && cents > 0;
        }

    }

    public record Relationships
    (
        PatreonDocument.Relationship campaign,
        PatreonDocument.Relationship user
    )
    {}

}
