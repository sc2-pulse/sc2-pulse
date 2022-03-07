// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.LadderTeamStateCollectionToArraySerializer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;

import javax.validation.constraints.NotNull;
import java.util.List;

public class CommonTeamHistory
{

    @NotNull
    private final List<LadderTeam> teams;

    @NotNull
    @JsonSerialize(using = LadderTeamStateCollectionToArraySerializer.class)
    private final List<LadderTeamState> states;

    public CommonTeamHistory(List<LadderTeam> teams, List<LadderTeamState> states)
    {
        this.teams = teams;
        this.states = states;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
    }

    public List<LadderTeamState> getStates()
    {
        return states;
    }

}
