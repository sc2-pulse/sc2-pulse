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
package com.nephest.battlenet.sc2.model.local;

import java.util.Objects;

import javax.validation.constraints.*;

import com.nephest.battlenet.sc2.model.*;

public class LeagueStats
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    @NotNull
    private Long leagueId;

    @NotNull
    private Integer playerCount;

    @NotNull
    private Integer teamCount;

    public LeagueStats
    (
        Long leagueId,
        Integer playerCount,
        Integer teamCount,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.leagueId = leagueId;
        this.playerCount = playerCount;
        this.teamCount = teamCount;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLeagueId());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof LeagueStats) ) return false;

        LeagueStats otherStats = (LeagueStats) other;
        return getLeagueId() != null
            && getLeagueId().equals(otherStats.getLeagueId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            getClass().getSimpleName(),
            String.valueOf(getLeagueId())
        );
    }

    public void setLeagueId(Long leagueId)
    {
        this.leagueId = leagueId;
    }

    public Long getLeagueId()
    {
        return leagueId;
    }

    public void setPlayerCount(Integer playerCount)
    {
        this.playerCount = playerCount;
    }

    public Integer getPlayerCount()
    {
        return playerCount;
    }

    public void setTeamCount(Integer teamCount)
    {
        this.teamCount = teamCount;
    }

    public Integer getTeamCount()
    {
        return teamCount;
    }

}
