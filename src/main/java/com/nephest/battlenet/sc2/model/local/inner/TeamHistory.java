// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record TeamHistory
(
    @NotNull Map<TeamHistoryDAO.StaticColumn, ?> staticData,
    @NotNull Map<TeamHistoryDAO.HistoryColumn, List<?>> history
)
{

    public TeamHistory
    (
        @NotNull Map<TeamHistoryDAO.StaticColumn, ?> staticData,
        @NotNull Map<TeamHistoryDAO.HistoryColumn, List<?>> history
    )
    {
        this.staticData = staticData;
        this.history = history;
    }

}
