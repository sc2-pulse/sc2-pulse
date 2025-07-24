// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.validation.ValidOriginalOrKeyBattleTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.regex.Pattern;

@ValidOriginalOrKeyBattleTag
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardAccount
extends BaseAccount
{

    public static final Pattern BATTLE_TAG_PATTERN = Pattern
        .compile("^([^#]+)#(\\d+)$");

    @NotNull
    private Long id;

    @Valid @NotNull
    private BlizzardAccountKey key;

    @JsonIgnore
    private transient Boolean isOriginalBattleTagValid;

    public BlizzardAccount(){}

    public BlizzardAccount
    (
        Long id,
        String battleTag,
        BlizzardAccountKey key
    )
    {
        super(battleTag);
        this.id = id;
        this.key = key;
    }

    @Override
    public String getBattleTag()
    {
        return isOriginalBattleTagValid() ? getOriginalBattleTag() : getKey().getBattleTag().toString();
    }

    @Override
    public void setBattleTag(String battleTag)
    {
        super.setBattleTag(battleTag);
        isOriginalBattleTagValid = null;
    }

    public Boolean isOriginalBattleTagValid()
    {
        if(isOriginalBattleTagValid == null)
            isOriginalBattleTagValid = BATTLE_TAG_PATTERN.matcher(getOriginalBattleTag()).matches();

        return isOriginalBattleTagValid;
    }

    public String getOriginalBattleTag()
    {
        return super.getBattleTag();
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public BlizzardAccountKey getKey()
    {
        return key;
    }

    public void setKey(BlizzardAccountKey key)
    {
        this.key = key;
    }

}
