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

import java.util.*;

import javax.validation.*;
import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.*;

import com.nephest.battlenet.sc2.model.*;

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
