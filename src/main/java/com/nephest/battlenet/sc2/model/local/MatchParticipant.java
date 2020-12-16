// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class MatchParticipant
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long matchId;

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private BaseMatch.Decision decision;

    public MatchParticipant(){}

    public MatchParticipant
    (
        @NotNull Long matchId, @NotNull Long playerCharacterId, @NotNull BaseMatch.Decision decision
    )
    {
        this.matchId = matchId;
        this.playerCharacterId = playerCharacterId;
        this.decision = decision;
    }

    public static MatchParticipant of(Match match, PlayerCharacter playerCharacter, BlizzardMatch blizzardMatch)
    {
        return new MatchParticipant(match.getId(), playerCharacter.getId(), blizzardMatch.getDecision());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchParticipant that = (MatchParticipant) o;
        return matchId.equals(that.matchId) && playerCharacterId.equals(that.playerCharacterId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(matchId, playerCharacterId);
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s %s]", getClass().getSimpleName(), matchId, playerCharacterId);
    }

    public Long getMatchId()
    {
        return matchId;
    }

    public void setMatchId(Long matchId)
    {
        this.matchId = matchId;
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public BaseMatch.Decision getDecision()
    {
        return decision;
    }

    public void setDecision(BaseMatch.Decision decision)
    {
        this.decision = decision;
    }

}
