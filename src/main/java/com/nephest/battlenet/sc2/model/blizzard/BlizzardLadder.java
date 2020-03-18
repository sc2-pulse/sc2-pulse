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

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardLadder
{

    private static final BlizzardTeam[] EMPTY_TEAM_ARRAY = new BlizzardTeam[0];

    //ladder can be empty
    @Valid
    @JsonProperty("team")
    private BlizzardTeam[] teams = EMPTY_TEAM_ARRAY;

    public BlizzardLadder(){}

    public BlizzardLadder(BlizzardTeam[] teams)
    {
        this.teams = teams;
    }

    public void setTeams(BlizzardTeam[] teams)
    {
        this.teams = teams;
    }

    public BlizzardTeam[] getTeams()
    {
        return teams;
    }

}

