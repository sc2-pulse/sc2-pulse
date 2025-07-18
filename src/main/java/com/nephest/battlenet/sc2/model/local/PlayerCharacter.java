// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.util.DiscriminatedTag;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;

@JsonIgnoreProperties(value={"discriminatedTag"}, allowGetters=true)
public class PlayerCharacter
extends BasePlayerCharacter
implements java.io.Serializable, PlayerCharacterNaturalId
{

    private static final long serialVersionUID = 7L;

    public static final Comparator<PlayerCharacter> NATURAL_ID_COMPARATOR =
        Comparator.comparing(PlayerCharacter::getRegion)
            .thenComparing(PlayerCharacter::getRealm)
            .thenComparing(PlayerCharacter::getBattlenetId);
    public static final int FAKE_DISCRIMINATOR = 1;
    public static final String DISCRIMINATOR_DELIMITER = "#";

    private Long id;

    @NotNull
    private Long accountId;

    @NotNull
    private Region region;

    @NotNull
    private Long battlenetId;

    private transient DiscriminatedTag discriminatedTag;

    public PlayerCharacter(){}

    public PlayerCharacter(Long id, Long accountId, Region region, Long battlenetId, Integer realm, String name)
    {
        super(realm, name);
        this.id = id;
        this.accountId = accountId;
        this.region = region;
        this.battlenetId = battlenetId;
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

    public static PlayerCharacter of(Account account, PlayerCharacterNaturalId naturalId, String name)
    {
        return new PlayerCharacter
        (
            null,
            account.getId(),
            naturalId.getRegion(),
            naturalId.getBattlenetId(),
            naturalId.getRealm(),
            name
        );
    }

    public static boolean isFakeDiscriminator(Integer discriminator)
    {
        return discriminator != null && discriminator == FAKE_DISCRIMINATOR;
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
        if ( !(other instanceof PlayerCharacter otherPlayerCharacter) ) return false;

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

    @Override
    public void setName(String name)
    {
        super.setName(name);
        this.discriminatedTag = null;
    }

    private void parseDiscriminatedTag()
    {
        discriminatedTag = getName() == null || Account.isFakeBattleTag(getName())
            ? DiscriminatedTag.EMPTY
            : DiscriminatedTag.parse
            (
                getName(),
                DiscriminatedTag.DEFAULT_DELIMITER,
                i->isFakeDiscriminator(i) ? null : i
            );
    }

    @JsonUnwrapped
    public DiscriminatedTag getDiscriminatedTag()
    {
        if(discriminatedTag == null) parseDiscriminatedTag();
        return discriminatedTag;
    }

}

