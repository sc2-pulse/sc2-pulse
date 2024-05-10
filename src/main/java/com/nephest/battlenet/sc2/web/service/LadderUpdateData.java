// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import java.util.Collections;
import java.util.List;

public class LadderUpdateData
{

    public static final LadderUpdateData EMPTY = new LadderUpdateData(true, List.of());

    private final boolean allStats;
    private final List<PendingLadderData> data;

    public LadderUpdateData(boolean allStats, List<PendingLadderData> data)
    {
        this.allStats = allStats;
        this.data = Collections.unmodifiableList(data);
    }

    public boolean isAllStats()
    {
        return allStats;
    }

    public List<PendingLadderData> getData()
    {
        return data;
    }

}
