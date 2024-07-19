// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LadderUpdateData
{

    public static final LadderUpdateData EMPTY = new LadderUpdateData(true, List.of(), List.of());

    private final boolean allStats;
    private final List<PendingLadderData> data;
    private final List<Map<Region, LadderUpdateTaskContext<Void>>> contexts;

    public LadderUpdateData
    (
        boolean allStats,
        List<PendingLadderData> data,
        List<Map<Region, LadderUpdateTaskContext<Void>>> contexts
    )
    {
        this.allStats = allStats;
        this.data = Collections.unmodifiableList(data);
        this.contexts = Collections.unmodifiableList(contexts);
    }

    public boolean isAllStats()
    {
        return allStats;
    }

    public List<PendingLadderData> getData()
    {
        return data;
    }

    public List<Map<Region, LadderUpdateTaskContext<Void>>> getContexts()
    {
        return contexts;
    }

}
