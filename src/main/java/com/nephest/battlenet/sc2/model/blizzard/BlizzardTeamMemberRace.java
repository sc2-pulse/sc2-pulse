// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nephest.battlenet.sc2.model.Race;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class BlizzardTeamMemberRace
{

    @NotNull
    private Race race;

    @NotNull
    @JsonProperty("count")
    private Integer gamesPlayed;

    public BlizzardTeamMemberRace(){}

    public BlizzardTeamMemberRace(Race race, Integer gamesPlayed)
    {
        this.race = race;
        this.gamesPlayed = gamesPlayed;
    }

    @JsonProperty("race_short")
    public void setRace(Race race)
    {
        this.race = race;
    }

    @JsonProperty("race_short")
    public Race getRace()
    {
        return race;
    }

    public void setGamesPlayed(Integer gamesPlayed)
    {
        this.gamesPlayed = gamesPlayed;
    }

    public Integer getGamesPlayed()
    {
        return gamesPlayed;
    }

    @JsonProperty("race")
    public void setRace(Map<String, String> race)
    {
        this.race = Race.from(race.get("en_US"));
    }

}
