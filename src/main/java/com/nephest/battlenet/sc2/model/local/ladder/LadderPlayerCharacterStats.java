// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import jakarta.validation.constraints.NotNull;

public class LadderPlayerCharacterStats
{

    @NotNull
    private final PlayerCharacterStats stats;

    @NotNull
    private final LadderPlayerSearchStats previousStats;

    @NotNull
    private final LadderPlayerSearchStats currentStats;

    public LadderPlayerCharacterStats
    (PlayerCharacterStats stats, LadderPlayerSearchStats previousStats, LadderPlayerSearchStats currentStats)
    {
        this.stats = stats;
        this.previousStats = previousStats;
        this.currentStats = currentStats;
    }

    public PlayerCharacterStats getStats()
    {
        return stats;
    }

    public LadderPlayerSearchStats getPreviousStats()
    {
        return previousStats;
    }

    public LadderPlayerSearchStats getCurrentStats()
    {
        return currentStats;
    }

}
