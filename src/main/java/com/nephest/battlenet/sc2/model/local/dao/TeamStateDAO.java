// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.TeamState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class TeamStateDAO
{

    public static final String STD_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.division_id AS \"team_state.division_id\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\" ";

    public static final String CREATE_QUERY =
        "INSERT INTO team_state (team_id, \"timestamp\", division_id, games, rating) "
        + "VALUES (:teamId, :timestamp, :divisionId, :games, :rating)";


    public static final RowMapper<TeamState> STD_ROW_MAPPER = (rs, i)->
    new TeamState
    (
        rs.getLong("team_state.team_id"),
        rs.getObject("team_state.timestamp", OffsetDateTime.class),
        rs.getInt("team_state.division_id"),
        rs.getInt("team_state.games"),
        rs.getInt("team_state.rating")
    );

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public TeamStateDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }

    public static MapSqlParameterSource createParameterSource(TeamState history)
    {
        return new MapSqlParameterSource()
            .addValue("teamId", history.getTeamId())
            .addValue("timestamp", history.getDateTime())
            .addValue("divisionId", history.getDivisionId())
            .addValue("games", history.getGames())
            .addValue("rating", history.getRating());
    }

    public int[] saveState(TeamState... states)
    {
        if(states.length == 0) return new int[0];

        MapSqlParameterSource[] params = new MapSqlParameterSource[states.length];
        for(int i = 0; i < states.length; i++) params[i] = createParameterSource(states[i]);

        return template.batchUpdate(CREATE_QUERY, params);
    }

}
