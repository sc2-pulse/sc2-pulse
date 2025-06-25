// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypedTeamHistoryHistoryData
(
    @JsonProperty("TIMESTAMP") List<Long> timestamps,
    @JsonProperty("RATING") List<Integer> ratings,
    @JsonProperty("GAMES") List<Integer> games,
    @JsonProperty("WINS") List<Integer> wins,
    @JsonProperty("LEAGUE_TYPE") List<Integer> leagueTypes,
    @JsonProperty("TIER_TYPE") List<Integer> tierTypes,
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

    @SuppressWarnings("unchecked")
    public static TypedTeamHistoryHistoryData from(RawTeamHistoryHistoryData raw)
    {
        return new TypedTeamHistoryHistoryData
        (
            (List<Long>) raw.data().get(TeamHistoryDAO.HistoryColumn.TIMESTAMP),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.RATING),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.GAMES),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.WINS),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.LEAGUE_TYPE),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.TIER_TYPE),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.DIVISION_ID),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.GLOBAL_RANK),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.REGION_RANK),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.LEAGUE_RANK),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.GLOBAL_TEAM_COUNT),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.REGION_TEAM_COUNT),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.LEAGUE_TEAM_COUNT),
            (List<Long>) raw.data().get(TeamHistoryDAO.HistoryColumn.ID),
            (List<Integer>) raw.data().get(TeamHistoryDAO.HistoryColumn.SEASON)
        );
    }

}
