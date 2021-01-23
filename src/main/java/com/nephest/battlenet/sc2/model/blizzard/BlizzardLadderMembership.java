// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import javax.validation.constraints.NotNull;

public class BlizzardLadderMembership
{

    @NotNull
    private Long ladderId;

    @NotNull
    private String localizedGameMode;

    public BlizzardLadderMembership(){}

    public Long getLadderId()
    {
        return ladderId;
    }

    public void setLadderId(Long ladderId)
    {
        this.ladderId = ladderId;
    }

    public String getLocalizedGameMode()
    {
        return localizedGameMode;
    }

    public void setLocalizedGameMode(@NotNull String localizedGameMode)
    {
        this.localizedGameMode = localizedGameMode;
    }

}
