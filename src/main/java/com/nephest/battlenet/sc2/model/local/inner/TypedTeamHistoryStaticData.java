// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypedTeamHistoryStaticData
(
    String legacyUid
)
implements TeamHistoryStaticData
{

    public static TypedTeamHistoryStaticData from(RawTeamHistoryStaticData raw)
    {
        return new TypedTeamHistoryStaticData
        (
            (String) raw.data().get(TeamHistoryDAO.StaticColumn.LEGACY_UID)
        );
    }

}
