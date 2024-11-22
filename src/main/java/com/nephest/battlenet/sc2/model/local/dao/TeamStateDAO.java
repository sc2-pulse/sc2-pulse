// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.TeamState;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public static final int TEAM_SNAPSHOT_BATCH_SIZE = 200;

    public static final String STD_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.division_id AS \"team_state.division_id\", "
        + "team_state.wins AS \"team_state.wins\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.global_rank AS \"team_state.global_rank\", "
        + "team_state.region_rank AS \"team_state.region_rank\", "
        + "team_state.league_rank AS \"team_state.league_rank\", "
        + "team_state.secondary AS \"team_state.secondary\" ";

    public static final String SHORT_SELECT =
        "team_state.team_id AS \"team_state.team_id\", "
        + "team_state.\"timestamp\" AS \"team_state.timestamp\", "
        + "team_state.wins AS \"team_state.wins\", "
        + "team_state.games AS \"team_state.games\", "
        + "team_state.rating AS \"team_state.rating\", "
        + "team_state.global_rank AS \"team_state.global_rank\", "
        + "team_state.region_rank AS \"team_state.region_rank\", "
        + "team_state.league_rank AS \"team_state.league_rank\" ";

    public static final String CREATE_QUERY =
        "INSERT INTO team_state (team_id, \"timestamp\", division_id, games, rating, secondary) "
        + "VALUES (:teamId, :timestamp, :divisionId, :games, :rating, :secondary)";

    private static final String TAKE_TEAM_SNAPSHOT =
        "INSERT INTO team_state "
        + "("
            + "team_id, \"timestamp\", division_id, wins, games, rating, secondary, "
            + "global_rank, region_rank, league_rank, population_state_id"
        + ") "
        + "SELECT team.id, "
        + "CASE WHEN :timestamp::timestamp with time zone IS NULL "
            + "THEN last_played "
            + "ELSE :timestamp::timestamp with time zone END, "
        + "division_id, wins, wins + losses, rating, "
        + "CASE WHEN team.queue_type != :mainQueueType THEN true ELSE null::boolean END, "
        + "global_rank, region_rank, league_rank, "
        + "team.population_state_id "
        + "FROM team "
        + "WHERE team.id IN(:teamIds)";

    public static final String REMOVE_EXPIRED_TEMPLATE = """
        WITH delete_filter AS
        (
            SELECT team_id, timestamp
            FROM team_state
            LEFT JOIN team_state_archive USING(team_id, timestamp)
            WHERE timestamp >= :from AND timestamp < :to
            %1$s
            AND team_state_archive.team_id IS NULL
        )
            DELETE FROM team_state
            USING delete_filter
            WHERE team_state.team_id = delete_filter.team_id
                AND team_state.timestamp = delete_filter.timestamp
        """;
    private static final String REMOVE_EXPIRED_MAIN_QUERY = REMOVE_EXPIRED_TEMPLATE.formatted("");
    private static final String REMOVE_EXPIRED_SECONDARY_QUERY =
        REMOVE_EXPIRED_TEMPLATE.formatted("AND secondary = true");

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
        DAOUtils.getInteger(rs, "team_state.region_rank"),
        DAOUtils.getInteger(rs, "team_state.league_rank"),
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
        DAOUtils.getInteger(rs, "team_state.region_rank"),
        DAOUtils.getInteger(rs, "team_state.league_rank"),
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
    public int[] saveState(Set<TeamState> states)
    {
        if(states.isEmpty()) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = states.stream()
            .map(TeamStateDAO::createParameterSource)
            .toArray(MapSqlParameterSource[]::new);

        return template.batchUpdate(CREATE_QUERY, params);
    }

    private int takeSnapshotBatch(List<Long> teamIds, OffsetDateTime timestamp)
    {
        if(teamIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("mainQueueType", conversionService.convert(TeamState.MAIN_QUEUE_TYPE, Integer.class))
            .addValue("teamIds", Set.copyOf(teamIds))
            .addValue("timestamp", timestamp, Types.TIMESTAMP_WITH_TIMEZONE);
        return template.update(TAKE_TEAM_SNAPSHOT, params);
    }

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
     *     calls {@link #takeSnapshot(List, OffsetDateTime) takeSnapshot} with null datetime.
     * </p>
     * @param teamIds team ids to create snapshots of
     * @return number of created snapshots
     */
    @Transactional
    public int takeSnapshot(List<Long> teamIds)
    {
        return takeSnapshot(teamIds, null);
    }

    public int remove(OffsetDateTime from, OffsetDateTime to, boolean main)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("to", to);
        String query = main ? REMOVE_EXPIRED_MAIN_QUERY : REMOVE_EXPIRED_SECONDARY_QUERY;
        return template.update(query, params);
    }

    public Integer getCount(Region region, OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("from", from);
        return template.query(GET_COUNT_BY_TIMESTAMP_START_AND_REGION, params, DAOUtils.INT_EXTRACTOR);
    }

}
