// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderPlayerCharacterStatsDAO
{

    private static String FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final SeasonDAO seasonDAO;

    private static RowMapper<LadderPlayerCharacterStats> STD_ROW_MAPPER;

    public LadderPlayerCharacterStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        SeasonDAO seasonDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.seasonDAO = seasonDAO;
        initQueries(conversionService);
        initMappers(conversionService);
    }

    private static void initQueries(ConversionService conversionService)
    {
        if(FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY == null)
            FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY = initSelectQuery(conversionService);
    }

    private static String initSelectQuery(ConversionService conversionService)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("WITH ");
        for(Race race : Race.values())
        {
            sb.append(String.format("%1$s_filter AS "
            + "( "
            + "SELECT season, queue_type, team_type, "
            + conversionService.convert(race, Integer.class) + " AS race, "
            + "MAX(rating) FILTER "
            + "( "
            + "WHERE %1$s_games_played > 0 "
            + PlayerCharacterStatsDAO.getRaceTeamFilter(race)
            + ") AS rating, "
            + "SUM(%1$s_games_played) FILTER "
            + "( "
            + "WHERE %1$s_games_played > 0 "
            + PlayerCharacterStatsDAO.getRaceTeamFilter(race)
            + ") AS games "
            + "FROM team_member "
            + "INNER JOIN team ON team_member.team_id = team.id "
            + "WHERE player_character_id = :playerCharacterId "
            + "AND team.season IN(:season, :season - 1) "
            + "GROUP BY team.season, team.queue_type, team.team_type "
            + "), ", race.getName().toLowerCase()));
        }
        sb.append
        (
            "norace_filter AS  "
            + "(  "
                + "SELECT season, queue_type, team_type, NULL::integer AS race,  "
                + "MAX(rating) AS rating,  "
                + "SUM(team.wins) + SUM(team.losses) + SUM(team.ties) AS games "
                + "FROM team_member  "
                + "INNER JOIN team ON team_member.team_id = team.id  "
                + "WHERE player_character_id = :playerCharacterId "
                + "AND team.season IN(:season, :season - 1) "
                + "GROUP BY team.season, team.queue_type, team.team_type  "
            + "), "
            + "all_filter AS "
            + "("
                + "SELECT * FROM norace_filter "
                + "UNION "
                + "SELECT * FROM terran_filter "
                + "UNION "
                + "SELECT * FROM protoss_filter "
                + "UNION "
                + "SELECT * FROM zerg_filter "
                + "UNION "
                + "SELECT * FROM random_filter "
            + "), "
            + "prev_stats AS "
            + "("
                + "SELECT * FROM all_filter "
                + "WHERE season = :season - 1"
            + "), "
            + "cur_stats AS "
            + "("
                + "SELECT * FROM all_filter "
                + "WHERE season = :season"
            + ") "
            + "SELECT id, "
            + "player_character_stats.queue_type, "
            + "player_character_stats.team_type, "
            + "player_character_id, "
            + "player_character_stats.race, "
            + "rating_max, league_max, games_played, "
            + "prev_stats.rating AS rating_prev, "
            + "prev_stats.games AS games_prev, "
            + "cur_stats.rating AS rating_cur, "
            + "cur_stats.games AS games_cur "
            + "FROM player_character_stats "
            + "LEFT JOIN prev_stats ON player_character_stats.queue_type = prev_stats.queue_type "
                + "AND player_character_stats.team_type = prev_stats.team_type "
                + "AND COALESCE(player_character_stats.race, -32768) = COALESCE(prev_stats.race, -32768) "
            + "LEFT JOIN cur_stats ON player_character_stats.queue_type = cur_stats.queue_type "
                + "AND player_character_stats.team_type = cur_stats.team_type "
                + "AND COALESCE(player_character_stats.race, -32768) = COALESCE(cur_stats.race, -32768) "
            + "WHERE player_character_id = :playerCharacterId"
        );
        return sb.toString();
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) -> new LadderPlayerCharacterStats
        (
            PlayerCharacterStatsDAO.getStdRowMapper().mapRow(rs, num),
            new LadderPlayerSearchStats
            (
                DAOUtils.getInteger(rs, "rating_prev"),
                DAOUtils.getInteger(rs, "games_prev"),
                null
            ),
            new LadderPlayerSearchStats
            (
                DAOUtils.getInteger(rs, "rating_cur"),
                DAOUtils.getInteger(rs, "games_cur"),
                null
            )
        );
    }

    public static RowMapper<LadderPlayerCharacterStats> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public List<LadderPlayerCharacterStats> findGlobalList(Long playerCharacterId)
    {
        return template.query
        (
            FIND_GLOBAL_STATS_LIST_BY_PLAYER_CHARACTER_ID_QUERY,
            new MapSqlParameterSource()
                .addValue("playerCharacterId", playerCharacterId)
                .addValue("season", seasonDAO.getMaxBattlenetId()),
            STD_ROW_MAPPER
        );
    }

    public Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> findGlobalMap(Long playerCharacterId)
    {
        return transform(findGlobalList(playerCharacterId));
    }

    public static Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> transform(List<LadderPlayerCharacterStats> list)
    {
        Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> result = new EnumMap<>(QueueType.class);
        for(LadderPlayerCharacterStats stats : list)
        {
            Map<TeamType, Map<Race, LadderPlayerCharacterStats>> teams = result
                .computeIfAbsent(stats.getStats().getQueueType(),r->new EnumMap<>(TeamType.class));
            Map<Race, LadderPlayerCharacterStats> races = teams
                .computeIfAbsent(stats.getStats().getTeamType(),r->new HashMap<>(Race.values().length + 1, 1));
            races.put(stats.getStats().getRace(), stats);
        }
        return result;
    }

}
