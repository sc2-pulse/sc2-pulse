// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.Hibernate;

@Entity
public class PatreonDonationState
{

    @Id @NotNull
    @Column(nullable = false)
    private String memberId;

    @NotNull
    @Column(nullable = false)
    private String currency;

    @NotNull
    @Column(nullable = false)
    private BigDecimal lifetimeAmount;

    @Version
    private Short version;

    public PatreonDonationState()
    {
    }

    public PatreonDonationState
    (
        String memberId,
        String currency,
        BigDecimal lifetimeAmount
    )
    {
        this.memberId = memberId;
        this.currency = currency;
        this.lifetimeAmount = lifetimeAmount;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getMemberId());
    }

    @Override
    public boolean equals(Object obj)
    {
        if(this == obj) return true;
        if(obj == null) return false;
        if(Hibernate.getClass(this) != Hibernate.getClass(obj)) return false;

        PatreonDonationState other = (PatreonDonationState) obj;
        return getMemberId() != null
            && getMemberId().equals(other.getMemberId());
    }

    @Override
    public String toString()
    {
        return "PatreonDonationState{" + "memberId='" + memberId + "'}";
    }

    public String getMemberId()
    {
        return memberId;
    }

    public void setMemberId(String memberId)
    {
        this.memberId = memberId;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    public BigDecimal getLifetimeAmount()
    {
        return lifetimeAmount;
    }

    public void setLifetimeAmount(BigDecimal lifetimeAmount)
    {
        this.lifetimeAmount = lifetimeAmount;
    }

    public Short getVersion()
    {
        return version;
    }

    public void setVersion(Short version)
    {
        this.version = version;
    }

}
