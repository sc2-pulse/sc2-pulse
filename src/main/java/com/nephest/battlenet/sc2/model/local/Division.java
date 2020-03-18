/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local;

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;

public class Division
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

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

    public static final Division of(LeagueTier tier, BlizzardTierDivision bDivision)
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
        return getTierId() == otherDivision.getTierId()
            && getBattlenetId() == otherDivision.getBattlenetId();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            String.valueOf(getTierId()), String.valueOf(getBattlenetId())
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
