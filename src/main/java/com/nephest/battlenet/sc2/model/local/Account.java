// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class Account
extends BaseAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    private Long id;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public Account(){}

    public Account(Long id, String battleTag)
    {
        super(battleTag);
        this.id = id;
    }

    public static final Account of(BlizzardAccount bAccount)
    {
        return new Account(null, bAccount.getBattleTag());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBattleTag());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if( !(other instanceof Account) ) return false;

        Account otherAccount = (Account) other;
        return getBattleTag().equals(otherAccount.getBattleTag());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            getClass().getSimpleName(),
            getBattleTag()
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

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

}
