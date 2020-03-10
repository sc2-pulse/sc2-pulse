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

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMemberRace;

public class TeamMember
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    @NotNull
    private Long teamId;

    @NotNull
    private Long characterId;

    public TeamMember()
    {
        super();
    }

    public TeamMember
    (
        Long teamId,
        Long characterId,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.teamId = teamId;
        this.characterId = characterId;
    }

    public static final TeamMember of
    (
        Team team,
        PlayerCharacter character,
        BlizzardTeamMemberRace[] races
    )
    {
        TeamMember tm = new TeamMember();
        tm.setTeamId(team.getId());
        tm.setCharacterId(character.getId());
        for(BlizzardTeamMemberRace race : races)
        {
            switch(race.getRace())
            {
                case TERRAN:
                    tm.setTerranGamesPlayed(race.getGamesPlayed());
                    break;
                case PROTOSS:
                    tm.setProtossGamesPlayed(race.getGamesPlayed());
                    break;
                case ZERG:
                    tm.setZergGamesPlayed(race.getGamesPlayed());
                    break;
                case RANDOM:
                    tm.setRandomGamesPlayed(race.getGamesPlayed());
                    break;
            }
        }
        return tm;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getTeamId(), getCharacterId());
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if( !(other instanceof TeamMember) ) return false;

        TeamMember otherMember = (TeamMember) other;
        return getTeamId() == otherMember.getTeamId()
            && getCharacterId() == otherMember.getCharacterId();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            String.valueOf(getTeamId()), String.valueOf(getCharacterId())
        );
    }

    public void setTeamId(Long teamId)
    {
        this.teamId = teamId;
    }

    public Long getTeamId()
    {
        return teamId;
    }

    public void setCharacterId(Long characterId)
    {
        this.characterId = characterId;
    }

    public Long getCharacterId()
    {
        return characterId;
    }

}
