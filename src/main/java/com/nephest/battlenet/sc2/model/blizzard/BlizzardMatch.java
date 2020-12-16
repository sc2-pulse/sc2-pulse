// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.nephest.battlenet.sc2.model.BaseMatch;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class BlizzardMatch
extends BaseMatch
{

    @NotNull
    private Decision decision;

    public BlizzardMatch(){}

    public BlizzardMatch
    (
        @NotNull OffsetDateTime date,
        @NotNull MatchType type,
        @NotNull String map,
        @NotNull Decision decision
    )
    {
        super(date, type, map);
        this.decision = decision;
    }

    public Decision getDecision()
    {
        return decision;
    }

    public void setDecision(Decision decision)
    {
        this.decision = decision;
    }

}
