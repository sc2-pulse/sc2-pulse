// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.MatchParticipant;

import javax.validation.constraints.NotNull;

public class LadderMatchParticipant
{

    @NotNull
    private final LadderTeamMember teamMember;

    @NotNull
    private final MatchParticipant participant;

    public LadderMatchParticipant
    (
       @NotNull MatchParticipant participant,  @NotNull LadderTeamMember teamMember
    )
    {
        this.teamMember = teamMember;
        this.participant = participant;
    }

    public LadderTeamMember getTeamMember()
    {
        return teamMember;
    }

    public MatchParticipant getParticipant()
    {
        return participant;
    }

}
