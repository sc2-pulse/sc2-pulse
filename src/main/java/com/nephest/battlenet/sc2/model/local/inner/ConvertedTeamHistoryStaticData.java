// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import org.springframework.core.convert.ConversionService;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvertedTeamHistoryStaticData
(
    Long id,
    Region region,
    QueueType queueType,
    TeamType teamType,
    Integer season,
    TeamLegacyId legacyId
)
implements TeamHistoryStaticData
{

    public static ConvertedTeamHistoryStaticData from
    (
        TypedTeamHistoryStaticData typed,
        ConversionService conversionService
    )
    {
        return new ConvertedTeamHistoryStaticData
        (
            typed.id(),
            conversionService.convert(typed.region(), Region.class),
            conversionService.convert(typed.queueType(), QueueType.class),
            conversionService.convert(typed.teamType(), TeamType.class),
            typed.season(),
            TeamLegacyId.trusted(typed.legacyId())
        );
    }

}
