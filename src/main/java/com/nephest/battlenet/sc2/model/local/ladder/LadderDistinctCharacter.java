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

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;

import javax.validation.constraints.NotNull;

public class LadderDistinctCharacter
{

    @NotNull
    private final Region region;

    @NotNull
    private final BaseLeague.LeagueType leagueMax;

    @NotNull
    private final Integer ratingMax;

    @NotNull
    private final Integer totalGamesPlayed;

    @NotNull
    private final LadderTeamMember members;

    public LadderDistinctCharacter
    (
        Region region,
        BaseLeague.LeagueType leagueMax,
        Integer ratingMax,
        String battleTag,
        PlayerCharacter character,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed,
        Integer totalGamesPlayed
    )
    {
        this.region = region;
        this.leagueMax = leagueMax;
        this.ratingMax = ratingMax;
        this.totalGamesPlayed = totalGamesPlayed;
        this.members = new LadderTeamMember
        (
            battleTag,
            character,
            terranGamesPlayed,
            protossGamesPlayed,
            zergGamesPlayed,
            randomGamesPlayed
        );
    }

    public Region getRegion()
    {
        return region;
    }

    public BaseLeague.LeagueType getLeagueMax()
    {
        return leagueMax;
    }

    public Integer getRatingMax()
    {
        return ratingMax;
    }

    public Integer getTotalGamesPlayed()
    {
        return totalGamesPlayed;
    }

    public LadderTeamMember getMembers()
    {
        return members;
    }

}
