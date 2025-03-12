// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.LeagueStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueStatsDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(LeagueStatsDAO.class);

    public static final String STD_SELECT =
        "league_stats.league_id AS \"league_stats.league_id\","
        + "league_stats.team_count AS \"league_stats.team_count\", "
        + "league_stats.terran_games_played AS \"league_stats.terran_games_played\", "
        + "league_stats.protoss_games_played AS \"league_stats.protoss_games_played\", "
        + "league_stats.zerg_games_played AS \"league_stats.zerg_games_played\", "
        + "league_stats.random_games_played AS \"league_stats.random_games_played\", "
        + "league_stats.terran_team_count AS \"league_stats.terran_team_count\", "
        + "league_stats.protoss_team_count AS \"league_stats.protoss_team_count\", "
        + "league_stats.zerg_team_count AS \"league_stats.zerg_team_count\", "
        + "league_stats.random_team_count AS \"league_stats.random_team_count\" ";

    private static final String CALCULATE_SEASON_STATS_TEMPLATE =
        "WITH mandatory_stats AS "
        + "("
            + "SELECT "
            + "MAX(league.id) AS league_id, "
            + "COUNT(DISTINCT(team.id)) as team_count, "
            + "(COALESCE(SUM(team_member.terran_games_played), 0)) as terran_games_played, "
            + "(COALESCE(SUM(team_member.protoss_games_played), 0)) as protoss_games_played, "
            + "(COALESCE(SUM(team_member.zerg_games_played), 0)) as zerg_games_played, "
            + "(COALESCE(SUM(team_member.random_games_played), 0)) as random_games_played "

            + "FROM team_member "
            + "INNER JOIN team ON team_member.team_id=team.id "
            + "INNER JOIN division ON team.division_id=division.id "
            + "INNER JOIN league_tier ON division.league_tier_id=league_tier.id "
            + "INNER JOIN league ON league_tier.league_id=league.id "

            + "WHERE "
            + "team.season=:seasonId "
            + "GROUP BY team.region, team.league_type, team.queue_type, team.team_type"
        + "), "
        + "race_team_filter AS "
        + "( "
            + "SELECT :seasonId AS season, "
            + "queue_type, region, league_type, team_type, "
            + "substring(legacy_id::text from char_length(legacy_id::text))::bigint AS race, "
            + "COUNT(*) AS team_count "
            + "FROM team "
            + "WHERE season = :seasonId "
            + "AND team.queue_type = %1$s "
            + "GROUP BY team.region, team.league_type, team.queue_type, team.team_type, race "
        + "), "
        + "race_team_count AS "
        + "("
            + "SELECT "
            + "league.id AS league_id, race, SUM(team_count) AS team_count "
            + "FROM race_team_filter "
            + "INNER JOIN season ON race_team_filter.season = season.battlenet_id "
                + "AND race_team_filter.region = season.region "
            + "INNER JOIN league ON season.id = league.season_id "
                + "AND race_team_filter.league_type = league.type "
                + "AND race_team_filter.queue_type = league.queue_type "
                + "AND race_team_filter.team_type = league.team_type "
            + "GROUP BY league.id, race"
        + ") "
        + "INSERT INTO league_stats "
        + "("
            + "league_id, team_count, "
            + "terran_games_played, protoss_games_played, zerg_games_played, random_games_played,"
            + "terran_team_count, protoss_team_count, zerg_team_count, random_team_count"
        + ") "
        + "SELECT mandatory_stats.league_id, "
        + "mandatory_stats.team_count, "

        + "mandatory_stats.terran_games_played, "
        + "mandatory_stats.protoss_games_played, "
        + "mandatory_stats.zerg_games_played, "
        + "mandatory_stats.random_games_played, "

        + "terran_team_count.team_count, "
        + "protoss_team_count.team_count, "
        + "zerg_team_count.team_count, "
        + "random_team_count.team_count "

        + "FROM mandatory_stats "
        + "LEFT JOIN race_team_count AS terran_team_count "
            + "ON mandatory_stats.league_id = terran_team_count.league_id "
            + "AND terran_team_count.race = %2$s "
        + "LEFT JOIN race_team_count AS protoss_team_count "
            + "ON mandatory_stats.league_id = protoss_team_count.league_id "
            + "AND protoss_team_count.race = %3$s "
        + "LEFT JOIN race_team_count AS zerg_team_count "
            + "ON mandatory_stats.league_id = zerg_team_count.league_id "
            + "AND zerg_team_count.race = %4$s "
        + "LEFT JOIN race_team_count AS random_team_count "
            + "ON mandatory_stats.league_id = random_team_count.league_id "
            + "AND random_team_count.race = %5$s ";

    private static String CALCULATE_SEASON_STATS_QUERY;
    private static String CALCULATE_SEASON_STATS_MERGE_QUERY;

    public static final RowMapper<LeagueStats> STD_ROW_MAPPER = (rs, num) -> new LeagueStats
    (
        rs.getInt("league_stats.league_id"),
        rs.getInt("league_stats.team_count"),
        rs.getInt("league_stats.terran_games_played"),
        rs.getInt("league_stats.protoss_games_played"),
        rs.getInt("league_stats.zerg_games_played"),
        rs.getInt("league_stats.random_games_played"),

        DAOUtils.getInteger(rs, "league_stats.terran_team_count"),
        DAOUtils.getInteger(rs, "league_stats.protoss_team_count"),
        DAOUtils.getInteger(rs, "league_stats.zerg_team_count"),
        DAOUtils.getInteger(rs, "league_stats.random_team_count")
    );



    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LeagueStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        if(CALCULATE_SEASON_STATS_QUERY == null) initQueries(conversionService);
    }
    
    private static void initQueries(ConversionService conversionService)
    {
        CALCULATE_SEASON_STATS_QUERY = String.format
        (
            CALCULATE_SEASON_STATS_TEMPLATE,
            conversionService.convert(QueueType.LOTV_1V1, Integer.class),
            conversionService.convert(Race.TERRAN, Integer.class),
            conversionService.convert(Race.PROTOSS, Integer.class),
            conversionService.convert(Race.ZERG, Integer.class),
            conversionService.convert(Race.RANDOM, Integer.class)
        );
        CALCULATE_SEASON_STATS_MERGE_QUERY = CALCULATE_SEASON_STATS_QUERY
            + " "
            + "ON CONFLICT(league_id) DO UPDATE SET "
            + "team_count=excluded.team_count, "

            + "terran_games_played=excluded.terran_games_played, "
            + "protoss_games_played=excluded.protoss_games_played, "
            + "zerg_games_played=excluded.zerg_games_played, "
            + "random_games_played=excluded.random_games_played, "

            + "terran_team_count=excluded.terran_team_count, "
            + "protoss_team_count=excluded.protoss_team_count, "
            + "zerg_team_count=excluded.zerg_team_count, "
            + "random_team_count=excluded.random_team_count";
    }

    public void calculateForSeason(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_QUERY, params);
        LOG.debug("Calculated league stats for {} season", season);
    }

    public void mergeCalculateForSeason(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_MERGE_QUERY, params);
        LOG.debug("Calculated (merged) league stats for {} season", season);
    }

}


