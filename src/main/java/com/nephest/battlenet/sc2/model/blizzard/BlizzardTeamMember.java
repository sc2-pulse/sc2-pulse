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

import javax.validation.*;
import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlizzardTeamMember
{

    public static final BlizzardTeamMemberRace[] EMPTY_RACE_ARRAY
        = new BlizzardTeamMemberRace[0];

    @Valid @NotNull
    @JsonProperty("legacy_link")
    private BlizzardPlayerCharacter character;

    //races can be empty
    @Valid
    @JsonProperty("played_race_count")
    private BlizzardTeamMemberRace[] races = EMPTY_RACE_ARRAY;

    @Valid @NotNull
    @JsonProperty("character_link")
    private BlizzardAccount account;

    public BlizzardTeamMember(){}

    public BlizzardTeamMember
    (
        BlizzardPlayerCharacter character,
        BlizzardTeamMemberRace[] races,
        BlizzardAccount account
    )
    {
        this.character = character;
        this.races = races;
        this.account = account;
    }

    public void setCharacter(BlizzardPlayerCharacter character)
    {
        this.character = character;
    }

    public BlizzardPlayerCharacter getCharacter()
    {
        return character;
    }

    public void setRaces(BlizzardTeamMemberRace[] races)
    {
        this.races = races;
    }

    public BlizzardTeamMemberRace[] getRaces()
    {
        return races;
    }

    public void setAccount(BlizzardAccount account)
    {
        this.account = account;
    }

    public BlizzardAccount getAccount()
    {
        return account;
    }

}
