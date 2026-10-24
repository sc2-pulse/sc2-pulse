// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.Hibernate;

@Entity
@Table
(
    uniqueConstraints = @UniqueConstraint
    (
        name = "uq_donation_platform_donation_id",
        columnNames = {"platform", "donation_id"}
    ),
    indexes = @Index
    (
        name = "ix_donation_created_id",
        columnList = "created,id"
    )
)
public class Donation
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Valid @NotNull
    @Embedded
    private PlatformDonationRef platformRef;

    /*
        TODO replace individual overrides with @EmbeddedColumnNaming once Spring
        Boot supports it
    */
    @Valid @NotNull
    @Embedded
    @AttributeOverrides
    ({
        @AttributeOverride
        (
            name = "amount",
            column = @Column(name = "money_original_amount", nullable = false)
        ),
        @AttributeOverride
        (
            name = "currency",
            column = @Column(name = "money_original_currency", nullable = false)
        )
    })
    private GenericMoney moneyOriginal;

    @Valid @NotNull
    @Embedded
    @AttributeOverrides
    ({
        @AttributeOverride
        (
            name = "amount",
            column = @Column(name = "money_target_amount")
        ),
        @AttributeOverride
        (
            name = "currency",
            column = @Column(name = "money_target_currency", nullable = false)
        )
    })
    private GenericMoney moneyTarget;

    @NotNull
    @Column(nullable = false)
    private OffsetDateTime created;

    public Donation()
    {
    }

    public Donation
    (
        PlatformDonationRef platformRef,
        GenericMoney moneyOriginal,
        GenericMoney moneyTarget,
        OffsetDateTime created
    )
    {
        this.platformRef = platformRef;
        this.moneyOriginal = moneyOriginal;
        this.moneyTarget = moneyTarget;
        this.created = created;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPlatformRef());
    }

    @Override
    public boolean equals(Object obj)
    {
        if(this == obj) return true;
        if(obj == null) return false;
        if(Hibernate.getClass(this) != Hibernate.getClass(obj)) return false;

        Donation other = (Donation) obj;
        return getPlatformRef() != null
            && getPlatformRef().equals(other.getPlatformRef());
    }

    @Override
    public String toString()
    {
        return "Donation{" + "platformRef=" + platformRef + '}';
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public PlatformDonationRef getPlatformRef()
    {
        return platformRef;
    }

    public void setPlatformRef(PlatformDonationRef platformRef)
    {
        this.platformRef = platformRef;
    }

    public GenericMoney getMoneyOriginal()
    {
        return moneyOriginal;
    }

    public void setMoneyOriginal(GenericMoney moneyOriginal)
    {
        this.moneyOriginal = moneyOriginal;
    }

    public GenericMoney getMoneyTarget()
    {
        return moneyTarget;
    }

    public void setMoneyTarget(GenericMoney moneyTarget)
    {
        this.moneyTarget = moneyTarget;
    }

    public OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(OffsetDateTime created)
    {
        this.created = created;
    }

}
