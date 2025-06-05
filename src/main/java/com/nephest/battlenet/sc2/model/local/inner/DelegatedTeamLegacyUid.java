// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Team;

public class DelegatedTeamLegacyUid
extends TeamLegacyUid
{

    private final Team team;

    public DelegatedTeamLegacyUid(Team team)
    {
        this.team = team;
    }

    @Override
    public QueueType getQueueType()
    {
        return getTeam().getQueueType();
    }

    @Override
    public void setQueueType(QueueType queueType)
    {
        getTeam().setQueueType(queueType);
    }

    @Override
    public TeamType getTeamType()
    {
        return getTeam().getTeamType();
    }

    @Override
    public void setTeamType(TeamType teamType)
    {
        getTeam().setTeamType(teamType);
    }

    @Override
    public Region getRegion()
    {
        return getTeam().getRegion();
    }

    @Override
    public void setRegion(Region region)
    {
        getTeam().setRegion(region);
    }

    @Override
    public TeamLegacyId getId()
    {
        return getTeam().getLegacyId();
    }

    @Override
    public void setId(TeamLegacyId id)
    {
        getTeam().setLegacyId(id);
    }

    public Team getTeam()
    {
        return team;
    }
    
}
