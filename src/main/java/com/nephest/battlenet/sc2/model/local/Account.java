// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class Account
extends BaseAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    private Long id;

    @NotNull
    private Partition partition;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

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
            getClass().getSimpleName(),
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

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

}
