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

import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class PlayerCharacter
extends BasePlayerCharacter
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    private Long id;

    @NotNull
    private Long accountId;

    @NotNull
    private Region region;

    @NotNull
    private Long battlenetId;

    public PlayerCharacter(){}

    public PlayerCharacter(Long id, Long accountId, Region region, Long battlenetId, Integer realm, String name)
    {
        super(realm, name);
        this.id = id;
        this.accountId = accountId;
        this.region = region;
        this.battlenetId = battlenetId;
    }

    public static final PlayerCharacter of(Account account, Region region, BlizzardPlayerCharacter bCharacter)
    {
        return new PlayerCharacter
        (
            null,
            account.getId(),
            region,
            bCharacter.getId(),
            bCharacter.getRealm(),
            bCharacter.getName()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRegion(), getBattlenetId());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof PlayerCharacter) ) return false;

        PlayerCharacter otherPlayerCharacter = (PlayerCharacter) other;
        return getRegion() == otherPlayerCharacter.getRegion()
            && getBattlenetId().equals(otherPlayerCharacter.getBattlenetId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            getRegion().name(), getBattlenetId()
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

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public void setBattlenetId(Long battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public Long getBattlenetId()
    {
        return battlenetId;
    }

}

