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

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.core.convert.*;

import com.nephest.battlenet.sc2.model.local.*;

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
        + "GROUP BY team.region, team.league_type, team.queue_type, team.team_type "

        + "ON DUPLICATE KEY UPDATE "
        + "league_id=VALUES(league_id), "
        + "player_count=VALUES(player_count), "
        + "team_count=VALUES(team_count), "
        + "terran_games_played=VALUES(terran_games_played), "
        + "protoss_games_played=VALUES(protoss_games_played), "
        + "zerg_games_played=VALUES(zerg_games_played), "
        + "random_games_played=VALUES(random_games_played)";



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

}


