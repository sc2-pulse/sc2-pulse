// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.local.Season;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

public class LadderUpdateTaskContext<T>
extends LadderUpdateContext
{

    private final List<Future<T>> tasks;

    public LadderUpdateTaskContext
    (
        Season season,
        Map<QueueType, Set<BaseLeague.LeagueType>> data,
        List<Future<T>> tasks
    )
    {
        super(season, data);
        this.tasks = tasks;
    }

    public List<Future<T>> getTasks()
    {
        return tasks;
    }

}
