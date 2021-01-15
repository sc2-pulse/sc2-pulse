// Copyright (C) 2021 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import javax.validation.constraints.NotNull;

public class BlizzardLadderMembership
{

    @NotNull
    private String localizedGameMode;

    public BlizzardLadderMembership(){}

    public String getLocalizedGameMode()
    {
        return localizedGameMode;
    }

    public void setLocalizedGameMode(@NotNull String localizedGameMode)
    {
        this.localizedGameMode = localizedGameMode;
    }

}
