// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TeamHistorySummary
(
    @NotNull Map<TeamHistoryDAO.StaticColumn, ?> staticData,
    @NotNull Map<TeamHistoryDAO.SummaryColumn, ?> summary
)
{}
