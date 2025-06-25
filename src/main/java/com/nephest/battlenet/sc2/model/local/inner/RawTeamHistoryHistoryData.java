// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import java.util.Map;

public record RawTeamHistoryHistoryData
(
    @JsonAnyGetter Map<TeamHistoryDAO.HistoryColumn, List<?>> data
)
implements TeamHistoryHistoryData
{

    public static final RawTeamHistoryHistoryData EMPTY = new RawTeamHistoryHistoryData(Map.of());

    @JsonCreator
    public RawTeamHistoryHistoryData{}

}
