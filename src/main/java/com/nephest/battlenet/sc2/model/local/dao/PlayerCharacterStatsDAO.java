// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class PlayerCharacterStatsDAO
{

    public static final String CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START =
        "INSERT INTO player_character_stats "
        + "(player_character_id, season_id, queue_type, team_type, race, rating_max, league_max, games_played) "
        + "SELECT player_character.id, season.id, team.queue_type, team.team_type, %1$s, "
        + "MAX(team.rating), MAX(team.league_type), ";
    public static final String CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END =
        "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN season ON team.region=season.region AND team.season=season.battlenet_id ";
    public static final String CALCULATE_PLAYER_CHARACTER_STATS_GROUP =
        "GROUP BY season.id, team.queue_type, team.team_type, player_character.id ";
    public static final String MERGE_TEMPLATE =
        " "
        + "ON CONFLICT(player_character_id, COALESCE(season_id, -32768), COALESCE(race, -32768), " 
        + "queue_type, team_type) DO UPDATE SET "
        + "rating_max=excluded.rating_max, "
        + "league_max=excluded.league_max, "
        + "games_played=excluded.games_played";
    public static final String CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        String.format
        (
            CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START
            + "("
                + "COALESCE(SUM(terran_games_played), 0) "
                + "+ COALESCE(SUM(protoss_games_played), 0) "
                + "+ COALESCE(SUM(zerg_games_played), 0) "
                + "+ COALESCE(SUM(random_games_played), 0)"
            + ") "
            + CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END
            + "WHERE team.season=:season "
            + CALCULATE_PLAYER_CHARACTER_STATS_GROUP,
            "NULL"
        );
    public static final String CALCULATE_MERGE_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_QUERY
        + MERGE_TEMPLATE;
    public static final String CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERY_FORMAT =
        CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START
        + "SUM(%2$s_games_played) "
        + CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END
        + "WHERE team.season=:season "
        + "AND ("
            + "%2$s_games_played::decimal / "
            + "(team.wins + team.losses + team.ties) "
        + ") > 0.9::decimal "
        + CALCULATE_PLAYER_CHARACTER_STATS_GROUP;
    public static final String CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERY_FORMAT =
        CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERY_FORMAT
        + MERGE_TEMPLATE;
    
    public static final String CALCULATE_PLAYER_CHARACTER_GLOBAL_STATS = 
        "INSERT INTO player_character_stats "
        + "(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played) "
        + "SELECT player_character_id, queue_type, team_type, race, "
        + "MAX(rating_max) AS rating_max, MAX(league_max) AS league_max, SUM(games_played) AS games_played "
        + "FROM player_character_stats "
        + "WHERE season_id IS NOT NULL "
        + "GROUP BY player_character_id, race, queue_type, team_type";
    public static final String CALCULATE_MERGE_PLAYER_CHARACTER_GLOBAL_STATS =
        CALCULATE_PLAYER_CHARACTER_GLOBAL_STATS + MERGE_TEMPLATE;

    private static Map<Race, String> CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES;
    private static Map<Race, String> CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES;

    public static final String FIND_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY =
        "SELECT player_character_stats.id, season_id, queue_type, team_type, player_character_id, race, rating_max, "
        + "league_max, games_played "
        + "FROM player_character_stats "
        + "INNER JOIN player_character ON player_character_stats.player_character_id=player_character.id "
        + "WHERE player_character.id = :playerCharacterId";
    public static final String FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY =
        "SELECT id, season_id, queue_type, team_type, player_character_id, race, rating_max, league_max, games_played "
        + "FROM player_character_stats "
        + "WHERE player_character_id = :playerCharacterId AND season_id is NULL ";

    private final NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    public final RowMapper<PlayerCharacterStats> STD_ROW_MAPPER = (rs, num) ->
    {
        rs.getInt("race");
        Race race = rs.wasNull() ? null : conversionService.convert(rs.getInt("race"), Race.class);
        return new PlayerCharacterStats
        (
            rs.getLong("id"),
            rs.getLong("player_character_id"),
            rs.getLong("season_id"),
            conversionService.convert(rs.getInt("queue_type"), QueueType.class),
            conversionService.convert(rs.getInt("team_type"), TeamType.class),
            race,
            rs.getInt("rating_max"),
            conversionService.convert(rs.getInt("league_max"), BaseLeague.LeagueType.class),
            rs.getInt("games_played")
        );
    };

    public PlayerCharacterStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initQueries(conversionService);
    }

    private static void initQueries(ConversionService conversionService)
    {
        if(CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES = initQueries(CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERY_FORMAT, conversionService);
        if(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES = initQueries(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERY_FORMAT, conversionService);
    }

    private static Map<Race, String> initQueries(String query, ConversionService conversionService)
    {
        Map<Race, String> queries = new EnumMap<>(Race.class);
        for (Race race : Race.values())
        {
            String q = String.format
            (
                query,
                conversionService.convert(race, Integer.class),
                race.getName().toLowerCase()
            );
            queries.put(race, q);
        }
        return Collections.unmodifiableMap(queries);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void calculate(long season)
    {
        SqlParameterSource params = new MapSqlParameterSource().addValue("season", season);
        for(Race race : Race.values()) template.update(CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race), params);
        template.update(CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_QUERY, params);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void mergeCalculate(long season)
    {
        SqlParameterSource params = new MapSqlParameterSource().addValue("season", season);
        for(Race race : Race.values()) template.update(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race),
            params);
        template.update(CALCULATE_MERGE_PLAYER_CHARACTER_RACELESS_STATS_QUERY, params);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void calculateGlobal()
    {
        template.getJdbcTemplate().update(CALCULATE_PLAYER_CHARACTER_GLOBAL_STATS);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void mergeCalculateGlobal()
    {
        template.getJdbcTemplate().update(CALCULATE_MERGE_PLAYER_CHARACTER_GLOBAL_STATS);
    }

    public List<PlayerCharacterStats> findList(Long playerCharacterId)
    {
        return template.query
        (
            FIND_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY,
            new MapSqlParameterSource().addValue("playerCharacterId", playerCharacterId),
            STD_ROW_MAPPER
        );
    }

    public List<PlayerCharacterStats> findGlobalList(Long playerCharacterId)
    {
        return template.query
        (
            FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY,
            new MapSqlParameterSource().addValue("playerCharacterId", playerCharacterId),
            STD_ROW_MAPPER
        );
    }

    public Map<QueueType, Map<TeamType, Map<Race, PlayerCharacterStats>>> findGlobalMap(Long playerCharacterId)
    {
        Map<QueueType, Map<TeamType, Map<Race, PlayerCharacterStats>>> result = new EnumMap(QueueType.class);
        for(PlayerCharacterStats stats : findGlobalList(playerCharacterId))
        {
            Map<TeamType, Map<Race, PlayerCharacterStats>> teams = result
                .computeIfAbsent(stats.getQueueType(),r->new EnumMap(TeamType.class));
            Map<Race, PlayerCharacterStats> races = teams
                .computeIfAbsent(stats.getTeamType(),r->new HashMap(Race.values().length + 1, 1));
            races.put(stats.getRace(), stats);
        }
        return result;
    }

}
