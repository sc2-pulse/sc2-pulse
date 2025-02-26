// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import com.nephest.battlenet.sc2.model.util.DiscriminatedTag;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;

@JsonIgnoreProperties(value={"discriminatedTag"}, allowGetters=true)
public class Account
extends BaseAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 5L;

    public static final Comparator<Account> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Account::getPartition)
            .thenComparing(Account::getBattleTag);

    private Long id;

    @NotNull
    private Partition partition;

    private Boolean hidden;

    private transient DiscriminatedTag discriminatedTag;

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
        if( !(other instanceof Account otherAccount) ) return false;

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

    @Override
    public void setBattleTag(String battleTag)
    {
        super.setBattleTag(battleTag);
        this.discriminatedTag = null;
    }

    @JsonProperty("battleTag")
    public String getFakeOrRealBattleTag()
    {
        return getHidden() != null && getHidden()
            ? BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME
            : getBattleTag();
    }

    private void parseDiscriminatedTag()
    {
        discriminatedTag = getBattleTag() == null || Account.isFakeBattleTag(getBattleTag())
            ? DiscriminatedTag.EMPTY
            : DiscriminatedTag.parse(getBattleTag());
    }

    @JsonUnwrapped
    public DiscriminatedTag getDiscriminatedTag()
    {
        if(discriminatedTag == null) parseDiscriminatedTag();
        return discriminatedTag;
    }

}
