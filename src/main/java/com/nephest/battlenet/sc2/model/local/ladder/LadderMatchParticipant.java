// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.MatchParticipant;

import javax.validation.constraints.NotNull;

public class LadderMatchParticipant
{

    @NotNull
    private final MatchParticipant participant;

    @NotNull
    private final LadderTeam team;

    @NotNull
    private final LadderTeamState teamState;

    public LadderMatchParticipant(MatchParticipant participant, LadderTeam team, LadderTeamState teamState)
    {
        this.participant = participant;
        this.team = team;
        this.teamState = teamState;
    }

    public MatchParticipant getParticipant()
    {
        return participant;
    }

    public LadderTeam getTeam()
    {
        return team;
    }

    public LadderTeamState getTeamState()
    {
        return teamState;
    }

}
