// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.web.service.StatsService;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TeamStateDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(TeamStateDAO.class);

    @Value("${com.nephest.battlenet.sc2.mmr.history.main.length:#{'180'}}")
    private int MAX_DEPTH_DAYS_MAIN = 180;

    @Value("${com.nephest.battlenet.sc2.mmr.history.secondary.length:#{'180'}}")
    private int MAX_DEPTH_DAYS_SECONDARY = 180;

    public static final int TEAM_SNAPSHOT_BATCH_SIZE = 200;

    public static final String STD_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.division_id AS \"team_state.division_id\", "
        + "team_state.wins AS \"team_state.wins\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.global_rank AS \"team_state.global_rank\", "
        + "population_state.global_team_count AS \"team_state.global_team_count\", "
        + "team_state.region_rank AS \"team_state.region_rank\", "
        + "population_state.region_team_count AS \"team_state.region_team_count\", "
        + "team_state.archived AS \"team_state.archived\", "
        + "team_state.secondary AS \"team_state.secondary\" ";

    public static final String SHORT_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.wins AS \"team_state.wins\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.global_rank AS \"team_state.global_rank\", "
        + "population_state.global_team_count AS \"team_state.global_team_count\", "
        + "team_state.region_rank AS \"team_state.region_rank\", "
        + "population_state.region_team_count AS \"team_state.region_team_count\"";

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

    private static final String TAKE_TEAM_SNAPSHOT =
        "WITH last_population_snapshot AS"
        + "("
            + "SELECT id, league_id "
            + "FROM population_state "
            + "ORDER BY id DESC "
            + "LIMIT " +
                Region.values().length
                //-1 to offset 1v1 and archon
                * (QueueType.getTypes(StatsService.VERSION).size() - 1)
                * BaseLeague.LeagueType.values().length
                * TeamType.values().length
                * 2 //offset season change
        + "), "
        + "last_population_snapshot_filter AS"
        + "("
            + "SELECT DISTINCT ON(league_id) "
            + "* "
            + "FROM last_population_snapshot "
            + "ORDER BY league_id DESC, id DESC"
        + ") "
        + "INSERT INTO team_state "
        + "("
            + "team_id, \"timestamp\", division_id, wins, games, rating, secondary, "
            + "global_rank, region_rank, population_state_id"
        + ") "
        + "SELECT team.id, :timestamp, division_id, wins, wins + losses, rating, "
        + "CASE WHEN team.queue_type != :mainQueueType THEN true ELSE null::boolean END, "
        + "global_rank, region_rank,  "
        + "last_population_snapshot_filter.id "
        + "FROM team "
        + "INNER JOIN division ON team.division_id = division.id "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN last_population_snapshot_filter USING(league_id) "
        + "WHERE team.id IN(:teamIds)";

    public static final String REMOVE_EXPIRED_MAIN_QUERY =
        "DELETE FROM team_state "
        + "WHERE timestamp < :from "
        + "AND (archived IS NULL OR archived = false)";
    private static final String REMOVE_EXPIRED_SECONDARY_QUERY =
        "DELETE FROM team_state "
        + "WHERE secondary = true "
        + "AND timestamp < :from "
        + "AND (archived IS NULL OR archived = false)";

    private static final String GET_COUNT_BY_TIMESTAMP_START_AND_REGION =
        "SELECT COUNT(*) "
        + "FROM team_state "
        + "INNER JOIN team ON team_state.team_id = team.id "
        + "WHERE team_state.timestamp >= :from "
        + "AND team.region = :region";

    public static final RowMapper<TeamState> STD_ROW_MAPPER = (rs, i)->
    new TeamState
    (
        rs.getLong("team_state.team_id"),
        rs.getObject("team_state.timestamp", OffsetDateTime.class),
        rs.getInt("team_state.division_id"),
        DAOUtils.getInteger(rs, "team_state.wins"),
        rs.getInt("team_state.games"),
        rs.getInt("team_state.rating"),
        DAOUtils.getInteger(rs, "team_state.global_rank"),
        DAOUtils.getInteger(rs, "team_state.global_team_count"),
        DAOUtils.getInteger(rs, "team_state.region_rank"),
        DAOUtils.getInteger(rs, "team_state.region_team_count"),
        DAOUtils.getBoolean(rs, "team_state.archived"),
        DAOUtils.getBoolean(rs, "team_state.secondary")
    );

    public static final RowMapper<TeamState> SHORT_ROW_MAPPER = (rs, i)->
    new TeamState
    (
        rs.getLong("team_state.team_id"),
        rs.getObject("team_state.timestamp", OffsetDateTime.class),
        null,
        DAOUtils.getInteger(rs, "team_state.wins"),
        rs.getInt("team_state.games"),
        rs.getInt("team_state.rating"),
        DAOUtils.getInteger(rs, "team_state.global_rank"),
        DAOUtils.getInteger(rs, "team_state.global_team_count"),
        DAOUtils.getInteger(rs, "team_state.region_rank"),
        DAOUtils.getInteger(rs, "team_state.region_team_count"),
        null,
        null
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

    /**
     * <p>
     *     This method should be used only in tests. Production code should use
     *     {@link #takeSnapshot(List, OffsetDateTime) takeSnapshot} method to create snapshots.
     *     Use this method when you need to create a very specific team state in tests.
     * </p>
     * @param states states to save
     * @return batch numbers of saves states
     */
    public int[] saveState(TeamState... states)
    {
        if(states.length == 0) return new int[0];

        MapSqlParameterSource[] params = new MapSqlParameterSource[states.length];
        for(int i = 0; i < states.length; i++) params[i] = createParameterSource(states[i]);

        return template.batchUpdate(CREATE_QUERY, params);
    }

    private int takeSnapshotBatch(List<Long> teamIds, OffsetDateTime timestamp)
    {
        if(teamIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("mainQueueType", conversionService.convert(TeamState.MAIN_QUEUE_TYPE, Integer.class))
            .addValue("teamIds", teamIds.stream().distinct().collect(Collectors.toList()))
            .addValue("timestamp", timestamp);
        return template.update(TAKE_TEAM_SNAPSHOT, params);
    }

    /**
     * <p>
     *     Creates team snapshots. Uses last population snapshot, make sure you called
     *     {@link com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO#takeSnapshot(Collection) takeSnapshot}
     *     before calling this method, otherwise old values will be used.
     * </p>
     * @param teamIds team ids to create snapshots of
     * @param timestamp timestamp of snapshots
     * @return number of created snapshots
     */
    @Transactional
    public int takeSnapshot(List<Long> teamIds, OffsetDateTime timestamp)
    {
        if(teamIds.isEmpty()) return 0;

        int count = 0;
        for(int i = 0; i < teamIds.size();)
        {
            int to = Math.min(i + TEAM_SNAPSHOT_BATCH_SIZE, teamIds.size());
            List<Long> batch = teamIds.subList(i, to);
            count += takeSnapshotBatch(batch, timestamp);
            i = to;
        }
        return count;
    }

    /**
     * <p>
     *     calls {@link #takeSnapshot(List, OffsetDateTime) takeSnapshot} with current datetime.
     * </p>
     * @param teamIds team ids to create snapshots of
     * @return number of created snapshots
     */
    @Transactional
    public int takeSnapshot(List<Long> teamIds)
    {
        return takeSnapshot(teamIds, OffsetDateTime.now());
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

    public Integer getCount(Region region, OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("from", from);
        return template.query(GET_COUNT_BY_TIMESTAMP_START_AND_REGION, params, DAOUtils.INT_EXTRACTOR);
    }

}
