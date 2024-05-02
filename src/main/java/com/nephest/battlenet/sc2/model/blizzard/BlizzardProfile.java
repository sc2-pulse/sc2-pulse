// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import jakarta.validation.constraints.NotNull;

public class BlizzardProfile
{

    @NotNull
    private BlizzardLegacyProfile summary;

    public BlizzardProfile(){}

    public BlizzardProfile(BlizzardLegacyProfile summary)
    {
        this.summary = summary;
    }

    public BlizzardLegacyProfile getSummary()
    {
        return summary;
    }

    public void setSummary(BlizzardLegacyProfile summary)
    {
        this.summary = summary;
    }

}
