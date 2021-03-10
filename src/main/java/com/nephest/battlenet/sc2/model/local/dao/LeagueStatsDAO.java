// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.LeagueStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        + "league_stats.random_games_played AS \"league_stats.random_games_played\" ";

    private static final String CALCULATE_SEASON_STATS_QUERY =
        "INSERT INTO league_stats "
        + "(league_id, team_count, terran_games_played, protoss_games_played, zerg_games_played, random_games_played) "

        + "SELECT "
        + "MAX(league.id), "
        + "COUNT(DISTINCT(team.id)) as team_count, "
        + "(COALESCE(SUM(team_member.terran_games_played), 0)) as games_terran, "
        + "(COALESCE(SUM(team_member.protoss_games_played), 0)) as games_protoss, "
        + "(COALESCE(SUM(team_member.zerg_games_played), 0)) as games_zerg, "
        + "(COALESCE(SUM(team_member.random_games_played), 0)) as games_random "

        + "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN division ON team.division_id=division.id "
        + "INNER JOIN league_tier ON division.league_tier_id=league_tier.id "
        + "INNER JOIN league ON league_tier.league_id=league.id "

        + "WHERE "
        + "team.season=:seasonId "
        + "GROUP BY team.region, team.league_type, team.queue_type, team.team_type";

    private static final String CALCULATE_SEASON_STATS_MERGE_QUERY = CALCULATE_SEASON_STATS_QUERY
        + " "
        + "ON CONFLICT(league_id) DO UPDATE SET "
        + "team_count=excluded.team_count, "
        + "terran_games_played=excluded.terran_games_played, "
        + "protoss_games_played=excluded.protoss_games_played, "
        + "zerg_games_played=excluded.zerg_games_played, "
        + "random_games_played=excluded.random_games_played";

    public static final RowMapper<LeagueStats> STD_ROW_MAPPER = (rs, num) -> new LeagueStats
    (
        rs.getInt("league_stats.league_id"),
        rs.getInt("league_stats.team_count"),
        rs.getInt("league_stats.terran_games_played"),
        rs.getInt("league_stats.protoss_games_played"),
        rs.getInt("league_stats.zerg_games_played"),
        rs.getInt("league_stats.random_games_played")
    );



    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LeagueStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
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


