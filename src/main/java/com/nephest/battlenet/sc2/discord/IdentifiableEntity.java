// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.model.IdentifiableLong;

public class IdentifiableEntity
implements IdentifiableLong, GuildWrapper
{

    private Long id;

    public IdentifiableEntity()
    {
    }

    public IdentifiableEntity(Long id)
    {
        this.id = id;
    }

    @Override
    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

}
