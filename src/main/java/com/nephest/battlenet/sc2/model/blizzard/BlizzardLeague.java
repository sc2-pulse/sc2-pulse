// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardLeague
extends BaseLeague
{

    @Valid @NotNull
    @JsonProperty("tier")
    private BlizzardLeagueTier[] tiers;

    public BlizzardLeague(){}

    public BlizzardLeague
    (
        LeagueType type, QueueType queueType, TeamType teamType,
        BlizzardLeagueTier[] tiers
    )
    {
        super(type, queueType, teamType);
        this.tiers = tiers;
    }

    public void setTiers(BlizzardLeagueTier[] tiers)
    {
        this.tiers = tiers;
    }

    public BlizzardLeagueTier[] getTiers()
    {
        return tiers;
    }

    public void setKey(Map<String, String> key)
    {
        setType(LeagueType.from(Integer.valueOf(key.get("league_id"))));
        setQueueType(QueueType.from(Integer.valueOf(key.get("queue_id"))));
        setTeamType(TeamType.from(Integer.valueOf(key.get("team_type"))));
    }

}
