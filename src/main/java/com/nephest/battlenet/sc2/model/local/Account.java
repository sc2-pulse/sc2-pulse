// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import java.util.Comparator;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class Account
extends BaseAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 5L;

    public static final Comparator<Account> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Account::getBattleTag)
            .thenComparing(Account::getPartition);

    private Long id;

    @NotNull
    private Partition partition;

    private Boolean hidden;

    public Account(){}

    public Account(Long id, Partition partition, String battleTag)
    {
        super(battleTag);
        this.id = id;
        this.partition = partition;
    }

    public Account(Long id, Partition partition, String battleTag, Boolean hidden)
    {
        super(battleTag);
        this.id = id;
        this.partition = partition;
        this.hidden = hidden;
    }

    public static Account of(BlizzardAccount bAccount, Region region)
    {
        return new Account(null, Partition.of(region), bAccount.getBattleTag());
    }

    public static boolean isFakeBattleTag(String battleTag)
    {
        return battleTag.startsWith("f#");
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

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    @Override @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getBattleTag()
    {
        return super.getBattleTag();
    }

    @JsonProperty("battleTag")
    public String getFakeOrRealBattleTag()
    {
        return getHidden() != null && getHidden()
            ? BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME
            : getBattleTag();
    }

}
