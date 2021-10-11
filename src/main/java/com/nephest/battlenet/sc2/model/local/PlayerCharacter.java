// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

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

    private static final long serialVersionUID = 4L;

    private Long id;

    @NotNull
    private Long accountId;

    @NotNull
    private Region region;

    @NotNull
    private Long battlenetId;

    private Integer clanId;

    public PlayerCharacter(){}

    public PlayerCharacter(Long id, Long accountId, Region region, Long battlenetId, Integer realm, String name)
    {
        super(realm, name);
        this.id = id;
        this.accountId = accountId;
        this.region = region;
        this.battlenetId = battlenetId;
    }

    public PlayerCharacter
    (Long id, Long accountId, Region region, Long battlenetId, Integer realm, String name, Integer clanId)
    {
        this(id, accountId, region, battlenetId, realm, name);
        this.clanId = clanId;
    }

    public static PlayerCharacter of(Account account, Region region, BlizzardPlayerCharacter bCharacter)
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
        return Objects.hash(getRegion(), getRealm(), getBattlenetId());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof PlayerCharacter) ) return false;

        PlayerCharacter otherPlayerCharacter = (PlayerCharacter) other;
        return getRegion() == otherPlayerCharacter.getRegion()
            && getRealm().equals(otherPlayerCharacter.getRealm())
            && getBattlenetId().equals(otherPlayerCharacter.getBattlenetId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s]",
            PlayerCharacter.class.getSimpleName(),
            getRegion().name(), getRealm(), getBattlenetId()
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

    public Integer getClanId()
    {
        return clanId;
    }

    public void setClanId(Integer clanId)
    {
        this.clanId = clanId;
    }

}

