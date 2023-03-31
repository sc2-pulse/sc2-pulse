// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.replaystats;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;

public class ReplayStatsPlayerCharacter
extends BlizzardFullPlayerCharacter
{

    @JsonAlias("players_id")
    private Long replayStatsId;

    @Override @JsonAlias("character_link_id")
    public void setId(Long id)
    {
        super.setId(id);
    }

    @Override @JsonAlias("server")
    public void setRegion(Region region)
    {
        super.setRegion(region);
    }

    @Override @JsonAlias("legacy_link_realm")
    public void setRealm(Integer realm)
    {
        super.setRealm(realm);
    }

    @Override @JsonAlias("players_name")
    public void setName(String name)
    {
        super.setName(name);
    }

    public Long getReplayStatsId()
    {
        return replayStatsId;
    }

    public void setReplayStatsId(Long replayStatsId)
    {
        this.replayStatsId = replayStatsId;
    }

}
