// Copyright (C) 2020-2021 Oleksandr Masniuk
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

    @NotNull
    private String map;

    public BlizzardMatch(){}

    public BlizzardMatch
    (
        @NotNull OffsetDateTime date,
        @NotNull MatchType type,
        @NotNull String map,
        @NotNull Decision decision
    )
    {
        super(date, type);
        this.decision = decision;
        this.map = map;
    }

    public Decision getDecision()
    {
        return decision;
    }

    public void setDecision(Decision decision)
    {
        this.decision = decision;
    }

    public String getMap()
    {
        return map;
    }

    public void setMap(String map)
    {
        this.map = map;
    }

}
