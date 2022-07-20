// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlayerCharacterStatsDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(PlayerCharacterStatsDAO.class);

    public static final String CHARACTER_RACIAL_FILTER_TEMPLATE =
        "WITH player_character_filter AS"
        + "("
            + "SELECT DISTINCT team_member.player_character_id "
            + "FROM team_member "
            + "WHERE %2$s_games_played > 0"
        + ") ";
    public static final String RECENT_CHARACTER_FILTER_TEMPLATE =
        "WITH player_character_filter AS"
        + "("
            + "SELECT DISTINCT team_member.player_character_id "
            + "FROM team_state "
            + "INNER JOIN team_member USING(team_id) "
            + "WHERE team_state.\"timestamp\" > :updatedMin "
            + "%1$s"
        + ") ";
    public static final String RECENT_CHARACTER_FILTER = String.format(RECENT_CHARACTER_FILTER_TEMPLATE, "");
    public static final String RECENT_CHARACTER_RACIAL_FILTER_TEMPLATE = String.format(
        RECENT_CHARACTER_FILTER_TEMPLATE,
        "AND team_member.%2$s_games_played > 0 ");
    public static final String CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START =
        "INSERT INTO player_character_stats "
        + "(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played) "
        + "SELECT team_member.player_character_id, team.queue_type, team.team_type, %1$s, "
        + "GREATEST(MAX(team.rating), MAX(archived_rating.rating)), MAX(team.league_type), ";
    public static final String CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END =
        "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "LEFT JOIN LATERAL ("
            + "SELECT rating FROM team_state "
            + "WHERE team_state.team_id = team.id "
            + "AND team_state.archived = true "
            + "ORDER BY team_state.rating DESC "
            + "LIMIT 1"
        + ") archived_rating ON true ";
    public static final String CALCULATE_FILTERED_PLAYER_CHARACTER_STATS_TEMPLATE_END =
        "FROM player_character_filter "
        + "INNER JOIN team_member USING(player_character_id) "
        + "INNER JOIN team ON team_member.team_id = team.id "
        + "LEFT JOIN LATERAL ("
            + "SELECT rating FROM team_state "
            + "WHERE team_state.team_id = team.id "
            + "AND team_state.archived = true "
            + "ORDER BY team_state.rating DESC "
            + "LIMIT 1"
        + ") archived_rating ON true ";
    public static final String CALCULATE_PLAYER_CHARACTER_STATS_GROUP =
        "GROUP BY team.queue_type, team.team_type, team_member.player_character_id ";
    public static final String MERGE_TEMPLATE =
        " "
        + "ON CONFLICT(player_character_id, COALESCE(race, -32768), queue_type, team_type) DO UPDATE SET "
        + "rating_max=excluded.rating_max, "
        + "league_max=excluded.league_max, "
        + "games_played=excluded.games_played "
        + "WHERE player_character_stats.games_played<>excluded.games_played";
    public static final String CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_TEMPLATE =
        CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START
        + "SUM(team.wins) + SUM(team.losses) + SUM(team.ties) "
        + "%2$s"
        + CALCULATE_PLAYER_CHARACTER_STATS_GROUP;
    public static final String CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        String.format(CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_TEMPLATE, "NULL", CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END);
    public static final String CALCULATE_MERGE_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        String.format(CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_TEMPLATE + MERGE_TEMPLATE, "NULL", CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_END);
    public static final String CALCULATE_RECENT_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        String.format(
            RECENT_CHARACTER_FILTER + CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_TEMPLATE,
            "NULL", CALCULATE_FILTERED_PLAYER_CHARACTER_STATS_TEMPLATE_END
        );
    public static final String CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACELESS_STATS_QUERY =
        String.format(
            RECENT_CHARACTER_FILTER + CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_TEMPLATE + MERGE_TEMPLATE,
            "NULL", CALCULATE_FILTERED_PLAYER_CHARACTER_STATS_TEMPLATE_END
        );

    public static final String CALCULATE_PLAYER_CHARACTER_RACE_STATS_TEMPLATE =
        CHARACTER_RACIAL_FILTER_TEMPLATE
        + CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START
        + "SUM(%2$s_games_played) "
        + CALCULATE_FILTERED_PLAYER_CHARACTER_STATS_TEMPLATE_END
        + "WHERE "
        + "%2$s_games_played > 0 "
        + "%3$s "
        + CALCULATE_PLAYER_CHARACTER_STATS_GROUP;
    public static final String CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_TEMPLATE =
        CALCULATE_PLAYER_CHARACTER_RACE_STATS_TEMPLATE + MERGE_TEMPLATE;
    public static final String CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_TEMPLATE =
        RECENT_CHARACTER_RACIAL_FILTER_TEMPLATE
        + CALCULATE_PLAYER_CHARACTER_STATS_TEMPLATE_START
        + "SUM(%2$s_games_played) "
        + CALCULATE_FILTERED_PLAYER_CHARACTER_STATS_TEMPLATE_END
        + "WHERE "
        + "%2$s_games_played > 0 "
        + "%3$s "
        + CALCULATE_PLAYER_CHARACTER_STATS_GROUP;
    public static final String CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_TEMPLATE =
        CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_TEMPLATE + MERGE_TEMPLATE;

    private static Map<Race, String> CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES;
    private static Map<Race, String> CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES;
    private static Map<Race, String> CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES;
    private static Map<Race, String> CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES;

    public static final String FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY =
        "SELECT id, queue_type, team_type, player_character_id, race, rating_max, league_max, games_played "
            + "FROM player_character_stats "
            + "WHERE player_character_id = :playerCharacterId";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<PlayerCharacterStats> STD_ROW_MAPPER;

    public PlayerCharacterStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initQueries(conversionService);
        initMappers(conversionService);
    }

    private static void initQueries(ConversionService conversionService)
    {
        if(CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES =
                initQueries(CALCULATE_PLAYER_CHARACTER_RACE_STATS_TEMPLATE, conversionService);
        if(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES =
                initQueries(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_TEMPLATE, conversionService);
        if(CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES =
                initQueries(CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_TEMPLATE, conversionService);
        if(CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES == null)
            CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES =
                initQueries(CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_TEMPLATE, conversionService);
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
                race.getName().toLowerCase(),
                getRaceTeamFilter(race)
            );
            queries.put(race, q);
        }
        return Collections.unmodifiableMap(queries);
    }

    public static String getRaceTeamFilter(Race race)
    {
        StringBuilder sb = new StringBuilder();
        Race[] otherRaces = Arrays.stream(Race.values()).filter(r->r!=race).toArray(Race[]::new);
        for(Race otherRace : otherRaces) sb
            .append("AND ")
            .append(race.getName().toLowerCase())
            .append("_games_played > COALESCE(")
            .append(otherRace.getName().toLowerCase())
            .append("_games_played, 0) ");
        return sb.toString();
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) -> new PlayerCharacterStats
        (
            rs.getLong("id"),
            rs.getLong("player_character_id"),
            conversionService.convert(rs.getInt("queue_type"), QueueType.class),
            conversionService.convert(rs.getInt("team_type"), TeamType.class),
            DAOUtils.getConvertedObjectFromInteger(rs, "race", conversionService, Race.class),
            rs.getInt("rating_max"),
            conversionService.convert(rs.getInt("league_max"), BaseLeague.LeagueType.class),
            rs.getInt("games_played")
        );
    }

    public static RowMapper<PlayerCharacterStats> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int calculate()
    {
        int count = 0;
        for(Race race : Race.values())
            count += template.getJdbcTemplate().update(CALCULATE_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race));
        count += template.getJdbcTemplate().update(CALCULATE_PLAYER_CHARACTER_RACELESS_STATS_QUERY);
        LOG.debug("Calculated {} player character stats", count);
        return count;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeCalculate()
    {
        int count = 0;
        for(Race race : Race.values())
            count += template.getJdbcTemplate().update(CALCULATE_MERGE_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race));
        count += template.getJdbcTemplate().update(CALCULATE_MERGE_PLAYER_CHARACTER_RACELESS_STATS_QUERY);
        LOG.debug("Calculated (merged) {} player character stats", count);
        return count;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int calculate(OffsetDateTime updatedMin)
    {
        int count = 0;
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("updatedMin", updatedMin);
        for(Race race : Race.values())
            count += template.update(CALCULATE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race), params);
        count += template.update(CALCULATE_RECENT_PLAYER_CHARACTER_RACELESS_STATS_QUERY, params);
        LOG.debug("Calculated {} recent({}) player character stats", count, updatedMin);
        return count;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeCalculate(OffsetDateTime updatedMin)
    {
        int count = 0;
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("updatedMin", updatedMin);
        for(Race race : Race.values())
            count += template.update(CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACE_STATS_QUERIES.get(race), params);
        count += template.update(CALCULATE_MERGE_RECENT_PLAYER_CHARACTER_RACELESS_STATS_QUERY, params);
        LOG.debug("Calculated (merged) {} recent({}) player character stats", count, updatedMin);
        return count;
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
        Map<QueueType, Map<TeamType, Map<Race, PlayerCharacterStats>>> result = new EnumMap<>(QueueType.class);
        for(PlayerCharacterStats stats : findGlobalList(playerCharacterId))
        {
            Map<TeamType, Map<Race, PlayerCharacterStats>> teams = result
                .computeIfAbsent(stats.getQueueType(),r->new EnumMap<>(TeamType.class));
            Map<Race, PlayerCharacterStats> races = teams
                .computeIfAbsent(stats.getTeamType(),r->new HashMap<>(Race.values().length + 1, 1));
            races.put(stats.getRace(), stats);
        }
        return result;
    }

}
