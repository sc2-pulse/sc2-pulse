/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nephest.battlenet.sc2.model.Race;

import javax.validation.constraints.NotNull;
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
