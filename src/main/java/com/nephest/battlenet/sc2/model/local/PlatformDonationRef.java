// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@Embeddable
public record PlatformDonationRef
(
    @NotNull
    @Column(nullable = false)
    // TODO use @EnumeratedValue when Jakarta Persistence 3.2 is supported.
    @Enumerated(EnumType.STRING)
    DonationPlatform platform,

    @NotNull
    @Column(nullable = false)
    String donationId,

    String donorId,
    String donorName
)
{

    @Override
    public int hashCode()
    {
        return Objects.hash(platform(), donationId());
    }

    @Override
    public boolean equals(Object obj)
    {
        if(this == obj) return true;
        if(obj == null || obj.getClass() != getClass()) return false;

        PlatformDonationRef other = (PlatformDonationRef) obj;
        return platform() == other.platform()
            && Objects.equals(donationId(), other.donationId());
    }

    @Override
    public String toString()
    {
        return "PlatformDonationRef{"
            + "platform=" + platform
            + ", donationId='" + donationId
        + "'}";
    }

}
