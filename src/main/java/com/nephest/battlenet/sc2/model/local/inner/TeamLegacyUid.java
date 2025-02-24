// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Team;
import java.util.Objects;

public class TeamLegacyUid
{

    private QueueType queueType;
    private TeamType teamType;
    private Region region;
    private String id;

    public TeamLegacyUid()
    {
    }

    public TeamLegacyUid(QueueType queueType, TeamType teamType, Region region, String id)
    {
        this.queueType = queueType;
        this.teamType = teamType;
        this.region = region;
        this.id = id;
    }

    public static TeamLegacyUid of(Team team)
    {
        return new TeamLegacyUid
        (
            team.getQueueType(),
            team.getTeamType(),
            team.getRegion(),
            team.getLegacyId()
        );
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof TeamLegacyUid that)) {return false;}
        return getQueueType() == that.getQueueType()
            && getTeamType() == that.getTeamType()
            && getRegion() == that.getRegion()
            && getId().equals(that.getId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getQueueType(), getTeamType(), getRegion(), getId());
    }

    public QueueType getQueueType()
    {
        return queueType;
    }

    public void setQueueType(QueueType queueType)
    {
        this.queueType = queueType;
    }

    public TeamType getTeamType()
    {
        return teamType;
    }

    public void setTeamType(TeamType teamType)
    {
        this.teamType = teamType;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

}
