// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.ArrayToLadderTeamStateArrayListDeserializer;
import com.nephest.battlenet.sc2.config.convert.jackson.LadderTeamStateCollectionToArraySerializer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import java.util.List;
import javax.validation.constraints.NotNull;

public class CommonTeamHistory
{

    @NotNull
    private List<LadderTeam> teams;

    @NotNull
    @JsonSerialize(using = LadderTeamStateCollectionToArraySerializer.class)
    @JsonDeserialize(using = ArrayToLadderTeamStateArrayListDeserializer.class)
    private List<LadderTeamState> states;

    public CommonTeamHistory()
    {
    }

    public CommonTeamHistory(List<LadderTeam> teams, List<LadderTeamState> states)
    {
        this.teams = teams;
        this.states = states;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
    }

    public void setTeams(List<LadderTeam> teams)
    {
        this.teams = teams;
    }

    public List<LadderTeamState> getStates()
    {
        return states;
    }

    public void setStates(List<LadderTeamState> states)
    {
        this.states = states;
    }

}
