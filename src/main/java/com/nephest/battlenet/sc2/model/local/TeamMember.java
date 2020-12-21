// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMemberRace;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class TeamMember
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

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

    public static TeamMember of
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
        return getTeamId().equals(otherMember.getTeamId())
            && getCharacterId().equals(otherMember.getCharacterId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            TeamMember.class.getSimpleName(),
            getTeamId(), getCharacterId()
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
