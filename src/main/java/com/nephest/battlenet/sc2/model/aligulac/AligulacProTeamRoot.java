// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.aligulac;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AligulacProTeamRoot
{

    private AligulacProTeam team;

    public AligulacProTeamRoot(){}

    public AligulacProTeamRoot(AligulacProTeam team)
    {
        this.team = team;
    }

    public AligulacProTeam getTeam()
    {
        return team;
    }

    public void setTeam(AligulacProTeam team)
    {
        this.team = team;
    }

}
