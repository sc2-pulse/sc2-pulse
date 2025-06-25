// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import java.util.List;
import org.springframework.core.convert.ConversionService;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvertedTeamHistoryHistoryData
(
    @JsonProperty("TIMESTAMP") List<Long> timestamps,
    @JsonProperty("RATING") List<Integer> ratings,
    @JsonProperty("GAMES") List<Integer> games,
    @JsonProperty("WINS") List<Integer> wins,
    @JsonProperty("LEAGUE_TYPE") List<BaseLeague.LeagueType> leagueTypes,
    @JsonProperty("TIER_TYPE") List<BaseLeagueTier.LeagueTierType> tierTypes,
    @JsonProperty("DIVISION_ID") List<Integer> divisionIds,
    @JsonProperty("GLOBAL_RANK") List<Integer> globalRanks,
    @JsonProperty("REGION_RANK") List<Integer> regionRanks,
    @JsonProperty("LEAGUE_RANK") List<Integer> leagueRanks,
    @JsonProperty("GLOBAL_TEAM_COUNT") List<Integer> globalTeamCounts,
    @JsonProperty("REGION_TEAM_COUNT") List<Integer> regionTeamCunts,
    @JsonProperty("LEAGUE_TEAM_COUNT") List<Integer> leagueTeamCounts,
    @JsonProperty("ID") List<Long> ids,
    @JsonProperty("SEASON") List<Integer> seasons
)
implements TeamHistoryHistoryData
{

    public static ConvertedTeamHistoryHistoryData from
    (
        TypedTeamHistoryHistoryData typed,
        ConversionService conversionService
    )
    {
        return new ConvertedTeamHistoryHistoryData
        (
            typed.timestamps(),
            typed.ratings(),
            typed.games(),
            typed.wins(),
            typed.leagueTypes() == null
                ? null
                : typed.leagueTypes().stream()
                    .map(l->conversionService.convert(l, BaseLeague.LeagueType.class))
                    .toList(),
            typed.tierTypes() == null
                ? null
                : typed.tierTypes().stream()
                    .map(t->conversionService.convert(t, BaseLeagueTier.LeagueTierType.class))
                    .toList(),
            typed.divisionIds(),
            typed.globalRanks(),
            typed.regionRanks(),
            typed.leagueRanks(),
            typed.globalTeamCounts(),
            typed.regionTeamCunts(),
            typed.leagueTeamCounts(),
            typed.ids(),
            typed.seasons()
        );
    }

}
