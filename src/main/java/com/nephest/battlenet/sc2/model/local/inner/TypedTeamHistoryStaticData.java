// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypedTeamHistoryStaticData
(
    Long id,
    Integer region,
    Integer queueType,
    Integer teamType,
    Integer season,
    String legacyId
)
implements TeamHistoryStaticData
{

    public static TypedTeamHistoryStaticData from(RawTeamHistoryStaticData raw)
    {
        return new TypedTeamHistoryStaticData
        (
            (Long) raw.data().get(TeamHistoryDAO.StaticColumn.ID),
            (Integer) raw.data().get(TeamHistoryDAO.StaticColumn.REGION),
            (Integer) raw.data().get(TeamHistoryDAO.StaticColumn.QUEUE_TYPE),
            (Integer) raw.data().get(TeamHistoryDAO.StaticColumn.TEAM_TYPE),
            (Integer) raw.data().get(TeamHistoryDAO.StaticColumn.SEASON),
            (String) raw.data().get(TeamHistoryDAO.StaticColumn.LEGACY_ID)
        );
    }

}
