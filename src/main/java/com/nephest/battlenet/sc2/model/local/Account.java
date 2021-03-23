// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class Account
extends BaseAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 4L;

    private Long id;

    @NotNull
    private Partition partition;

    public Account(){}

    public Account(Long id, Partition partition, String battleTag)
    {
        super(battleTag);
        this.id = id;
        this.partition = partition;
    }

    public static Account of(BlizzardAccount bAccount, Region region)
    {
        return new Account(null, Partition.of(region), bAccount.getBattleTag());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBattleTag(), getPartition());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if( !(other instanceof Account) ) return false;

        Account otherAccount = (Account) other;
        return getBattleTag().equals(otherAccount.getBattleTag()) && getPartition() == otherAccount.getPartition();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            Account.class.getSimpleName(),
            getPartition(), getBattleTag()
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

    public Partition getPartition()
    {
        return partition;
    }

    public void setPartition(Partition partition)
    {
        this.partition = partition;
    }

}
