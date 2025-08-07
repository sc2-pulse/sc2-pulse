// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import jakarta.validation.constraints.NotNull;

public class BlizzardKey
{

    @NotNull
    private String href;

    public BlizzardKey()
    {
    }

    public BlizzardKey(String href)
    {
        this.href = href;
    }

    public String getHref()
    {
        return href;
    }

    public void setHref(String href)
    {
        this.href = href;
    }

}
