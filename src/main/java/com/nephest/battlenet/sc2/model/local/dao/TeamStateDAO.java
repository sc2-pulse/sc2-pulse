// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.TeamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Set;

@Repository
public class TeamStateDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(TeamStateDAO.class);

    @Value("${com.nephest.battlenet.sc2.mmr.history.main.length:#{'180'}}")
    private int MAX_DEPTH_DAYS_MAIN = 180;

    @Value("${com.nephest.battlenet.sc2.mmr.history.secondary.length:#{'180'}}")
    private int MAX_DEPTH_DAYS_SECONDARY = 180;

    public static final String STD_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.division_id AS \"team_state.division_id\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.global_rank AS \"team_state.global_rank\", "
        + "team_state.global_team_count AS \"team_state.global_team_count\", "
        + "team_state.region_rank AS \"team_state.region_rank\", "
        + "team_state.region_team_count AS \"team_state.region_team_count\", "
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
            + "WHERE timestamp >= :from "
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
        + "AND team_state.timestamp >= :from "
        + "AND (team_state.rating > COALESCE(all_filter.rating_max, -1) "
            + "OR team_state.rating < all_filter.rating_min)";

    public static final String CLEAR_ARCHIVE_QUERY =
        "WITH "
        + "team_filter AS "
        + "( "
            + "SELECT DISTINCT(team_state.team_id) "
            + "FROM team_state "
            + "WHERE timestamp >= :from "
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

    private static final String UPDATE_RANK_QUERY =
        "WITH "
        + "cheaters AS "
        + "("
            + String.format(TeamDAO.FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE, "DISTINCT(team_id)")
        + "),"
        + "cheaters_region AS "
        + "( "
            + String.format(TeamDAO.FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE,
                "COUNT(DISTINCT(team.id)) as count, season AS battlenet_id, region, queue_type, team_type") + " "
            + "GROUP BY season, region, queue_type, team_type"
        + "), "
        + "cheaters_global AS "
        + "( "
            + "SELECT battlenet_id AS season, queue_type, team_type, SUM(count) AS count "
            + "FROM cheaters_region "
            + "GROUP BY battlenet_id, queue_type, team_type "
        + "), "
        + "region_team_count AS "
        + "("
            + "SELECT season.battlenet_id AS season, queue_type, team_type, region, "
            + "SUM(team_count) - COALESCE(MAX(cheaters_region.count), 0) AS count,  "
            + "SUM(team_count) AS count_original "
            + "FROM league_stats "
            + "INNER JOIN league ON league_stats.league_id = league.id "
            + "INNER JOIN season ON league.season_id = season.id "
            + "LEFT JOIN cheaters_region USING(battlenet_id, region ,queue_type, team_type) "
            + "WHERE season.battlenet_id IN(:seasons) "
            + "GROUP BY season.battlenet_id, season.region, queue_type, team_type "
        + "), "
        + "global_team_count AS "
        + "("
            + "SELECT season, queue_type, team_type, "
            + "SUM(region_team_count.count_original) - COALESCE(MAX(cheaters_global.count), 0) AS count  "
            + "FROM region_team_count "
            + "LEFT JOIN cheaters_global USING(season, queue_type, team_type) "
            + "GROUP BY season, queue_type, team_type "
        + ") "
        + "UPDATE team_state "
        + "SET global_rank = team.global_rank, "
        + "global_team_count = global_team_count.count, "
        + "region_rank = team.region_rank, "
        + "region_team_count = region_team_count.count "
        + "FROM team "
        + "INNER JOIN region_team_count ON team.season = region_team_count.season "
            + "AND team.queue_type = region_team_count.queue_type "
            + "AND team.team_type = region_team_count.team_type "
            + "AND team.region = region_team_count.region "
        + "INNER JOIN global_team_count ON team.season = global_team_count.season "
            + "AND team.queue_type = global_team_count.queue_type "
            + "AND team.team_type = global_team_count.team_type "
        + "WHERE team_state.team_id = team.id "
        + "AND team_state.timestamp >= :from "
        + "AND team_state.global_rank IS NULL "
        + "AND team_state.team_id NOT IN (SELECT team_id FROM cheaters)";

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
        DAOUtils.getInteger(rs, "team_state.global_rank"),
        DAOUtils.getInteger(rs, "team_state.global_team_count"),
        DAOUtils.getInteger(rs, "team_state.region_rank"),
        DAOUtils.getInteger(rs, "team_state.region_team_count"),
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

    public int getMaxDepthDaysMain()
    {
        return MAX_DEPTH_DAYS_MAIN;
    }

    public int getMaxDepthDaysSecondary()
    {
        return MAX_DEPTH_DAYS_SECONDARY;
    }

    protected void setMaxDepthDaysMain(int days)
    {
        MAX_DEPTH_DAYS_MAIN = days;
    }

    protected void setMaxDepthDaysSecondary(int days)
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

    public int updateRanks(OffsetDateTime from, Set<Integer> seasons)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("seasons", seasons)
            .addValue
            (
                "cheaterReportType",
                conversionService.convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class)
            );
        int updated = template.update(UPDATE_RANK_QUERY, params);
        LOG.debug("Updated ranks of {} team states({}, {})", updated, seasons, from);
        return updated;
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
