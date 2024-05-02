// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import jakarta.validation.constraints.NotNull;

public class BlizzardMatches
{

    public static final BlizzardMatch[] EMPTY_MATCH_ARRAY = new BlizzardMatch[0];

    @NotNull
    private BlizzardMatch[] matches = EMPTY_MATCH_ARRAY;

    public BlizzardMatches(){}

    public BlizzardMatch[] getMatches()
    {
        return matches;
    }

    public void setMatches(BlizzardMatch[] matches)
    {
        this.matches = matches;
    }

}
