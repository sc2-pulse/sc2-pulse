// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class Division
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    private Integer id;

    @NotNull
    private Integer tierId;

    @NotNull
    private Long battlenetId;

    public Division(){}

    public Division(Integer id, Integer tierId, Long battlenetId)
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

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getId()
    {
        return id;
    }

    public void setTierId(Integer tierId)
    {
        this.tierId = tierId;
    }

    public Integer getTierId()
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
