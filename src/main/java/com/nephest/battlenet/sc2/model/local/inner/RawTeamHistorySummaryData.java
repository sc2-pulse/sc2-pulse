// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;

public record RawTeamHistorySummaryData(@JsonAnyGetter Map<TeamHistoryDAO.SummaryColumn, ?> data)
implements TeamHistorySummaryData
{

    public static final RawTeamHistorySummaryData EMPTY = new RawTeamHistorySummaryData(Map.of());

    @JsonCreator
    public RawTeamHistorySummaryData{}

}
