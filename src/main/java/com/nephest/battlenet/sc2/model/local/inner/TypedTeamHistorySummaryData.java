// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypedTeamHistorySummaryData
(
    Integer games,
    Integer ratingMin,
    Double ratingAvg,
    Integer ratingMax,
    Integer ratingLast,
    Integer regionRankLast,
    Integer regionTeamCountLast
)
implements TeamHistorySummaryData
{

    public static TypedTeamHistorySummaryData from(RawTeamHistorySummaryData raw)
    {
        return new TypedTeamHistorySummaryData
        (
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.GAMES),
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.RATING_MIN),
            (Double) raw.data().get(TeamHistoryDAO.SummaryColumn.RATING_AVG),
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.RATING_MAX),
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.RATING_LAST),
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.REGION_RANK_LAST),
            (Integer) raw.data().get(TeamHistoryDAO.SummaryColumn.REGION_TEAM_COUNT_LAST)
        );
    }

}
