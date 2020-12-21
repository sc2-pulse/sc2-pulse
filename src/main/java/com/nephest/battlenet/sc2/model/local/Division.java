// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class Division
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long tierId;

    @NotNull
    private Long battlenetId;

    public Division(){}

    public Division(Long id, Long tierId, Long battlenetId)
    {
        this.id = id;
        this.tierId = tierId;
        this.battlenetId = battlenetId;
    }

    public static Division of(LeagueTier tier, BlizzardTierDivision bDivision)
    {
        return new Division
        (
            null,
            tier.getId(),
            bDivision.getLadderId()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getTierId(), getBattlenetId());
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if( !(other instanceof Division) ) return false;

        Division otherDivision = (Division) other;
        return getTierId().equals(otherDivision.getTierId())
            && getBattlenetId().equals(otherDivision.getBattlenetId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            Division.class.getSimpleName(),
            getTierId(), getBattlenetId()
        );
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setTierId(Long tierId)
    {
        this.tierId = tierId;
    }

    public Long getTierId()
    {
        return tierId;
    }

    public void setLadderId(Long battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public Long getBattlenetId()
    {
        return battlenetId;
    }

}
