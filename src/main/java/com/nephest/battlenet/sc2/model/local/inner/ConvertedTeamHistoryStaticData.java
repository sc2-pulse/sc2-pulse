// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.StringToTeamLegacyIdConverter;
import com.nephest.battlenet.sc2.config.convert.jackson.TeamLegacyUidToStringConverter;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;
import org.springframework.core.convert.ConversionService;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvertedTeamHistoryStaticData
(
    @JsonSerialize(converter = TeamLegacyUidToStringConverter.class)
    @JsonDeserialize(converter = StringToTeamLegacyIdConverter.class)
    TeamLegacyUid teamLegacyUid
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
            conversionService.convert(typed.teamLegacyUid(), TeamLegacyUid.class)
        );
    }

}
