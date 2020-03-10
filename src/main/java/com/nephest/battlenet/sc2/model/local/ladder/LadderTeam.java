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
package com.nephest.battlenet.sc2.model.local.ladder;

import java.util.*;

import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.*;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeam
extends BaseTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private final LadderSeason season;

    @NotNull
    private final Region region;

    @NotNull
    private final BaseLeague league;

    @NotNull
    private final LeagueTier.LeagueTierType leagueTierType;


    private List<LadderTeamMember> members = Collections.emptyList();

    public LadderTeam
    (
        LadderSeason season,
        Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType leagueTierType,
        List<LadderTeamMember> members,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points
    )
    {
        super(rating, wins, losses, ties, points);
        this.season = season;
        this.region = region;
        this.league = league;
        this.leagueTierType = leagueTierType;
        this.members = members;
    }

    public LadderSeason getSeason()
    {
        return season;
    }

    public Region getRegion()
    {
        return region;
    }

    public BaseLeague getLeague()
    {
        return league;
    }

    public LeagueTier.LeagueTierType getLeagueTierType()
    {
        return leagueTierType;
    }

    public List<LadderTeamMember> getMembers()
    {
        return members;
    }

}

