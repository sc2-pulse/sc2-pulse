// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.local.Season;

public class LadderUpdateContext
{

    private final Season season;
    private final QueueType[] queues;
    private final BaseLeague.LeagueType[] leagues;

    public LadderUpdateContext(Season season, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        this.season = season;
        this.queues = queues;
        this.leagues = leagues;
    }

    public Season getSeason()
    {
        return season;
    }

    public QueueType[] getQueues()
    {
        return queues;
    }

    public BaseLeague.LeagueType[] getLeagues()
    {
        return leagues;
    }

}
