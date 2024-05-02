// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMemberRace;
import jakarta.validation.constraints.NotNull;
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
            tm.setGamesPlayed(race.getRace(), race.getGamesPlayed());
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
        if( !(other instanceof TeamMember otherMember) ) return false;

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
