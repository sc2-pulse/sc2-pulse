// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.TeamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class TeamStateDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(TeamStateDAO.class);

    private static int MAX_DEPTH_DAYS_MAIN = 120;
    private static int MAX_DEPTH_DAYS_SECONDARY = 120;

    public static final String STD_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.division_id AS \"team_state.division_id\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.archived AS \"team_state.archived\", "
        + "team_state.secondary AS \"team_state.secondary\" ";

    public static final String CREATE_QUERY =
        "INSERT INTO team_state (team_id, \"timestamp\", division_id, games, rating, secondary) "
        + "VALUES (:teamId, :timestamp, :divisionId, :games, :rating, :secondary)";
    
    public static final String ARCHIVE_QUERY =
        "WITH "
        + "team_filter AS "
        + "( "
            + "SELECT DISTINCT(team_state.team_id) "
            + "FROM team_state "
            + "WHERE timestamp > :from "
        + "), "
        + "min_max_filter AS "
        + "( "
            + "SELECT team_state.team_id, "
            + "MIN(team_state.rating) AS rating_min, "
            + "MAX(team_state.rating) AS rating_max "
            + "FROM team_filter "
            + "INNER JOIN team_state USING(team_id) "
            + "WHERE archived = true "
            + "GROUP BY team_state.team_id "
        + "), "
        + "all_filter AS "
        + "( "
            + "SELECT team_filter.team_id, min_max_filter.rating_min, min_max_filter.rating_max "
            + "FROM team_filter "
            + "LEFT JOIN min_max_filter USING(team_id) "
        + ") "
        + "UPDATE team_state "
        + "SET archived = true "
        + "FROM all_filter "
        + "WHERE team_state.team_id = all_filter.team_id "
        + "AND team_state.timestamp > :from "
        + "AND (team_state.rating > COALESCE(all_filter.rating_max, -1) "
            + "OR team_state.rating < all_filter.rating_min)";

    public static final String CLEAR_ARCHIVE_QUERY =
        "WITH "
        + "team_filter AS "
        + "( "
            + "SELECT DISTINCT(team_state.team_id) "
            + "FROM team_state "
            + "WHERE timestamp > :from "
        + "), "
        + "min_filter AS "
        + "( "
            + "SELECT DISTINCT ON (team_state.team_id) "
            + "team_state.team_id, team_state.timestamp "
            + "FROM team_filter "
            + "INNER JOIN team_state USING(team_id) "
            + "WHERE archived = true "
            + "ORDER BY team_state.team_id ASC, team_state.rating ASC, team_state.timestamp ASC "
        + "), "
        + "max_filter AS "
        + "( "
            + "SELECT DISTINCT ON (team_state.team_id) "
            + "team_state.team_id, team_state.timestamp "
            + "FROM team_filter "
            + "INNER JOIN team_state USING(team_id) "
            + "WHERE archived = true "
            + "ORDER BY team_state.team_id DESC, team_state.rating DESC, team_state.timestamp DESC "
        + ") "
        + "UPDATE team_state "
        + "SET archived = null "
        + "FROM min_filter "
        + "INNER JOIN max_filter USING(team_id) "
        + "WHERE team_state.team_id = min_filter.team_id "
        + "AND team_state.timestamp != min_filter.timestamp "
        + "AND team_state.timestamp != max_filter.timestamp "
        + "AND team_state.archived = true";

    public static final String REMOVE_EXPIRED_MAIN_QUERY =
        "DELETE FROM team_state "
        + "WHERE timestamp < :from "
        + "AND (archived IS NULL OR archived = false)";
    private static final String REMOVE_EXPIRED_SECONDARY_QUERY =
        "DELETE FROM team_state "
        + "WHERE secondary = true "
        + "AND timestamp < :from "
        + "AND (archived IS NULL OR archived = false)";

    public static final RowMapper<TeamState> STD_ROW_MAPPER = (rs, i)->
    new TeamState
    (
        rs.getLong("team_state.team_id"),
        rs.getObject("team_state.timestamp", OffsetDateTime.class),
        rs.getInt("team_state.division_id"),
        rs.getInt("team_state.games"),
        rs.getInt("team_state.rating"),
        DAOUtils.getBoolean(rs, "team_state.archived"),
        DAOUtils.getBoolean(rs, "team_state.secondary")
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public TeamStateDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public static int getMaxDepthDaysMain()
    {
        return MAX_DEPTH_DAYS_MAIN;
    }

    public static int getMaxDepthDaysSecondary()
    {
        return MAX_DEPTH_DAYS_SECONDARY;
    }

    protected static void setMaxDepthDaysMain(int days)
    {
        MAX_DEPTH_DAYS_MAIN = days;
    }

    protected  static void setMaxDepthDaysSecondary(int days)
    {
        MAX_DEPTH_DAYS_SECONDARY = days;
    }

    public static MapSqlParameterSource createParameterSource(TeamState history)
    {
        return new MapSqlParameterSource()
            .addValue("teamId", history.getTeamId())
            .addValue("timestamp", history.getDateTime())
            .addValue("divisionId", history.getDivisionId())
            .addValue("games", history.getGames())
            .addValue("rating", history.getRating())
            .addValue("secondary", history.getSecondary());
    }

    public int[] saveState(TeamState... states)
    {
        if(states.length == 0) return new int[0];

        MapSqlParameterSource[] params = new MapSqlParameterSource[states.length];
        for(int i = 0; i < states.length; i++) params[i] = createParameterSource(states[i]);

        return template.batchUpdate(CREATE_QUERY, params);
    }

    public void archive(OffsetDateTime from)
    {
        template
            .update(ARCHIVE_QUERY, new MapSqlParameterSource().addValue("from", from));
        LOG.debug("Archived team stated");
    }

    public void cleanArchive(OffsetDateTime from)
    {
        template
            .update(CLEAR_ARCHIVE_QUERY, new MapSqlParameterSource().addValue("from", from));
        LOG.debug("Cleaned team state archive");
    }

    public int removeExpired()
    {
        MapSqlParameterSource paramsMain = new MapSqlParameterSource()
            .addValue("from", OffsetDateTime.now().minusDays(getMaxDepthDaysMain()));
        MapSqlParameterSource paramsSecondary = new MapSqlParameterSource()
            .addValue("from", OffsetDateTime.now().minusDays(getMaxDepthDaysSecondary()));
        int removed1v1 = template.update(REMOVE_EXPIRED_MAIN_QUERY, paramsMain);
        int removedTeam = template.update(REMOVE_EXPIRED_SECONDARY_QUERY, paramsSecondary);
        LOG.debug("Removed expired team states({} 1v1, {} team)", removed1v1, removedTeam);
        return removed1v1 + removedTeam;
    }

}
