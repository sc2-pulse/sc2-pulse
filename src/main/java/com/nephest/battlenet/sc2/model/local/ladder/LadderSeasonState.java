// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class LadderSeasonState
{

    @NotNull @Valid
    private final SeasonState seasonState;

    @NotNull @Valid
    private final Season season;

    public LadderSeasonState(@NotNull @Valid SeasonState seasonState, @NotNull @Valid Season season)
    {
        this.seasonState = seasonState;
        this.season = season;
    }

    public SeasonState getSeasonState()
    {
        return seasonState;
    }

    public Season getSeason()
    {
        return season;
    }

}
