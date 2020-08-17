// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Repository
public class QueueStatsDAO
{

    private  static final Logger LOG = LoggerFactory.getLogger(QueueStatsDAO.class);

    private static final String CALCULATE_SEASON_PLAYER_BASE_QUERY =
        "WITH count_all "
        + "AS"
        + "("
            + "SELECT "
            + "MAX(team.season) AS \"team_season\", "
            + "team.queue_type AS \"team_queue_type\", "
            + "team.team_type AS \"team_team_type\", "
            + "COUNT(DISTINCT(account.id)) AS \"player_base\" "

            + "FROM team_member "
            + "INNER JOIN team ON team_member.team_id=team.id "
            + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
            + "INNER JOIN account ON player_character.account_id=account.id "

            + "WHERE "
            + "team.season<=:seasonId "
            + "GROUP BY team.queue_type, team.team_type "
        + ") "
        + "INSERT INTO queue_stats "
        + "(season, queue_type, team_type, player_base, player_count) "
        + "SELECT count_all.team_season, count_all.team_queue_type, count_all.team_team_type, count_all.player_base, 0 "
        + "FROM count_all "
        + "WHERE count_all.team_season = :seasonId";

    private static final String CALCULATE_SEASON_PLAYER_BASE_MERGE_QUERY = CALCULATE_SEASON_PLAYER_BASE_QUERY
        + " "
        + "ON CONFLICT(queue_type, team_type, season) DO UPDATE SET "
        + "player_base=excluded.player_base";

    private static final String CALCULATE_SEASON_STATS_QUERY =
        "INSERT INTO queue_stats "
        + "(season, queue_type, team_type, player_base, player_count) "
        + "SELECT MAX(team.season), team.queue_type, team.team_type, 0, COUNT(DISTINCT(account.id)) "
        + "FROM team_member "
        + "INNER JOIN team ON team_member.team_id = team.id "
        + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "WHERE team.season = :seasonId "
        + "GROUP BY team.queue_type, team.team_type";

    private static final String CALCULATE_SEASON_STATS_MERGE_QUERY = CALCULATE_SEASON_STATS_QUERY
        + " "
        + "ON CONFLICT(queue_type, team_type, season) DO UPDATE SET "
        + "player_count=excluded.player_count";

    private static final String FIND_QUEUE_STATS_BY_QUEUE_TYPE_AND_TEAM_TYPE =
        "SELECT id AS \"queue_stats.id\", season AS \"queue_stats.season\", "
        + "queue_type AS \"queue_stats.queue_type\", team_type AS \"queue_stats.team_type\", "
        + "player_base AS \"queue_stats.player_base\", "
        + "player_count AS \"queue_stats.player_count\" "
        + "FROM queue_stats "
        + "WHERE queue_type=:queueType AND team_type=:teamType "
        + "ORDER BY season";

    public final RowMapper<QueueStats> STD_ROW_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public QueueStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.STD_ROW_MAPPER = (rs, num) -> new QueueStats
        (
            rs.getLong("queue_stats.id"),
            rs.getInt("queue_stats.season"),
            conversionService.convert(rs.getInt("queue_stats.queue_type"), QueueType.class),
            conversionService.convert(rs.getInt("queue_stats.team_type"), TeamType.class),
            rs.getLong("queue_stats.player_base"),
            rs.getInt("queue_stats.player_count")
        );
    }

    public void calculateForSeason(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_QUERY, params);
        template.update(CALCULATE_SEASON_PLAYER_BASE_MERGE_QUERY, params);
        LOG.debug("Calculated queue stats for {} season", new Object[]{season});
    }

    public void mergeCalculateForSeason(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_MERGE_QUERY, params);
        template.update(CALCULATE_SEASON_PLAYER_BASE_MERGE_QUERY, params);
        LOG.debug("Calculated (merged) queue stats for {} season", new Object[]{season});
    }

    public List<QueueStats> findQueueStats(QueueType queueType, TeamType teamType)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return template.query(FIND_QUEUE_STATS_BY_QUEUE_TYPE_AND_TEAM_TYPE, params, STD_ROW_MAPPER);
    }

}
