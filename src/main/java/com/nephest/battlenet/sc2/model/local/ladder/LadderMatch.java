// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Match;

import javax.validation.constraints.NotNull;
import java.util.List;

public class LadderMatch
{

    @NotNull
    private final Match match;

    @NotNull
    private final List<LadderMatchParticipant> participants;

    public LadderMatch
    (
        @NotNull Match match, @NotNull List<LadderMatchParticipant> participants
    )
    {
        this.match = match;
        this.participants = participants;
    }

    public Match getMatch()
    {
        return match;
    }

    public List<LadderMatchParticipant> getParticipants()
    {
        return participants;
    }

}
