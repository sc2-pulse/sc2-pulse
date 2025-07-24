// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardAccount
extends BaseAccount
{

    @NotNull
    private Long id;

    @Valid @NotNull
    private BlizzardAccountKey key;

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
