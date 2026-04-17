// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nephest.battlenet.sc2.config.convert.jackson.UpperSnakeCaseStrategy;
import com.nephest.battlenet.sc2.model.BaseLeague;
import org.springframework.core.convert.ConversionService;

@JsonNaming(UpperSnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvertedTeamHistorySummaryData
(
    Integer games,
    BaseLeague.LeagueType leagueTypeMax,
    Short ratingMin,
    Double ratingAvg,
    Short ratingMax,
    Short ratingLast,
    Integer regionRankLast,
    Integer regionTeamCountLast
)
implements TeamHistorySummaryData
{

    public static ConvertedTeamHistorySummaryData from
    (
        TypedTeamHistorySummaryData data,
        ConversionService conversionService
    )
    {
        return new ConvertedTeamHistorySummaryData
        (
            data.games(),
            data.leagueTypeMax() == null
                ? null
                : conversionService.convert
                (
                    data.leagueTypeMax().intValue(),
                    BaseLeague.LeagueType.class
                ),
            data.ratingMin(),
            data.ratingAvg(),
            data.ratingMax(),
            data.ratingLast(),
            data.regionRankLast(),
            data.regionTeamCountLast()
        );
    }

}
