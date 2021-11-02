// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.SC2Map;

import javax.validation.constraints.NotNull;
import java.util.List;

public class LadderMatch
{

    @NotNull
    private final Match match;

    @NotNull
    private final SC2Map map;

    @NotNull
    private final List<LadderMatchParticipant> participants;

    public LadderMatch
    (
        @NotNull Match match, @NotNull SC2Map map, @NotNull List<LadderMatchParticipant> participants
    )
    {
        this.match = match;
        this.map = map;
        this.participants = participants;
    }

    public Match getMatch()
    {
        return match;
    }

    public SC2Map getMap()
    {
        return map;
    }

    public List<LadderMatchParticipant> getParticipants()
    {
        return participants;
    }

}
