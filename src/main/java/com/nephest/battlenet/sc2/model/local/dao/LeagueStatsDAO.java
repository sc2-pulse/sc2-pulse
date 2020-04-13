/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueStatsDAO
{
    private static final String CALCULATE_SEASON_STATS_QUERY =
        "INSERT INTO league_stats "
        + "(league_id, player_count, team_count, terran_games_played, protoss_games_played, zerg_games_played, random_games_played) "

        + "SELECT "
        + "MAX(league.id), "
        + "COUNT(DISTINCT(account.id)) as player_count, "
        + "COUNT(DISTINCT(team.id)) as team_count, "
        + "(COALESCE(SUM(team_member.terran_games_played), 0)) as games_terran, "
        + "(COALESCE(SUM(team_member.protoss_games_played), 0)) as games_protoss, "
        + "(COALESCE(SUM(team_member.zerg_games_played), 0)) as games_zerg, "
        + "(COALESCE(SUM(team_member.random_games_played), 0)) as games_random "

        + "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "INNER JOIN division ON team.division_id=division.id "
        + "INNER JOIN league_tier ON division.league_tier_id=league_tier.id "
        + "INNER JOIN league ON league_tier.league_id=league.id "

        + "WHERE "
        + "team.season=:seasonId "
        + "GROUP BY team.region, team.league_type, team.queue_type, team.team_type";

    private static final String CALCULATE_SEASON_STATS_MERGE_QUERY = CALCULATE_SEASON_STATS_QUERY
        + " "
        + "ON CONFLICT(league_id) DO UPDATE SET "
        + "player_count=excluded.player_count, "
        + "team_count=excluded.team_count, "
        + "terran_games_played=excluded.terran_games_played, "
        + "protoss_games_played=excluded.protoss_games_played, "
        + "zerg_games_played=excluded.zerg_games_played, "
        + "random_games_played=excluded.random_games_played";



    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    @Autowired
    public LeagueStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public void calculateForSeason(long season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_QUERY, params);
    }

    public void mergeCalculateForSeason(long season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season);
        template.update(CALCULATE_SEASON_STATS_MERGE_QUERY, params);
    }

}


