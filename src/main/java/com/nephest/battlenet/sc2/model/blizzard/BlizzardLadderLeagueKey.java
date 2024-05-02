// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BlizzardLadderLeagueKey
extends BaseLeague
{

    @NotNull
    private BaseLeague.LeagueType leagueId;

    @NotNull
    private Integer seasonId;

    @NotNull
    private QueueType queueId;

    @NotNull
    private TeamType teamType;

    public BaseLeague.LeagueType getLeagueId()
    {
        return leagueId;
    }

    public void setLeagueId(BaseLeague.LeagueType leagueId)
    {
        this.leagueId = leagueId;
    }

    public Integer getSeasonId()
    {
        return seasonId;
    }

    public void setSeasonId(Integer seasonId)
    {
        this.seasonId = seasonId;
    }

    public QueueType getQueueId()
    {
        return queueId;
    }

    public void setQueueId(QueueType queueId)
    {
        this.queueId = queueId;
    }

    public TeamType getTeamType()
    {
        return teamType;
    }

    public void setTeamType(TeamType teamType)
    {
        this.teamType = teamType;
    }

}
