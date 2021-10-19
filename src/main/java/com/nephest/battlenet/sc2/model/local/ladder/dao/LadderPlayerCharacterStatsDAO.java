// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            + "SELECT queue_type, team_type, "
            + conversionService.convert(race, Integer.class) + " AS race, "
            + "MAX(rating) FILTER "
            + "( "
            + "WHERE %1$s_games_played > 0 "
            + PlayerCharacterStatsDAO.getRaceTeamFilter(race)
            + ") AS rating_current, "
            + "SUM(%1$s_games_played) FILTER "
            + "( "
            + "WHERE %1$s_games_played > 0 "
            + PlayerCharacterStatsDAO.getRaceTeamFilter(race)
            + ") AS games_current "
            + "FROM team_member "
            + "INNER JOIN team ON team_member.team_id = team.id "
            + "WHERE player_character_id = :playerCharacterId "
            + "AND team.season = :season "
            + "GROUP BY team.queue_type, team.team_type "
            + "), ", race.getName().toLowerCase()));
        }
        sb.append
        (
            "norace_filter AS  "
            + "(  "
            + "SELECT queue_type, team_type, null AS race,  "
            + "MAX(rating) AS rating_current,  "
            + "("
            + "COALESCE(SUM(terran_games_played), 0) "
            + "+ COALESCE(SUM(protoss_games_played), 0) "
            + "+ COALESCE(SUM(zerg_games_played), 0) "
            + "+ COALESCE(SUM(random_games_played), 0)"
            + ") AS games_current "
            + "FROM team_member  "
            + "INNER JOIN team ON team_member.team_id = team.id  "
            + "WHERE player_character_id = :playerCharacterId "
            + "AND team.season = :season "
            + "GROUP BY team.queue_type, team.team_type  "
            + ") "
            + "SELECT id,"
            + " player_character_stats.queue_type, "
            + "player_character_stats.team_type, "
            + "player_character_id, "
            + "player_character_stats.race, "
            + "rating_max, league_max, games_played, "
            + "COALESCE(terran_filter.rating_current,  protoss_filter.rating_current,  zerg_filter.rating_current, "
            + "random_filter.rating_current, norace_filter.rating_current) AS rating_current, "
            + "COALESCE(terran_filter.games_current,  protoss_filter.games_current,  zerg_filter.games_current, "
            + "random_filter.games_current, norace_filter.games_current) AS games_current "
            + "FROM player_character_stats "
            + "LEFT JOIN terran_filter USING(queue_type, team_type, race) "
            + "LEFT JOIN protoss_filter USING(queue_type, team_type, race) "
            + "LEFT JOIN zerg_filter USING(queue_type, team_type, race) "
            + "LEFT JOIN random_filter USING(queue_type, team_type, race) "
            + "LEFT JOIN norace_filter ON player_character_stats.queue_type = norace_filter.queue_type "
            + "    AND player_character_stats.team_type = norace_filter.team_type "
            + "    AND player_character_stats.race IS NULL "
            + "WHERE player_character_id = :playerCharacterId"
        );
        return sb.toString();
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) -> new LadderPlayerCharacterStats
        (
            PlayerCharacterStatsDAO.getStdRowMapper().mapRow(rs, num),
            DAOUtils.getInteger(rs, "rating_current"),
            DAOUtils.getInteger(rs, "games_current")
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
        Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> result = new EnumMap<>(QueueType.class);
        for(LadderPlayerCharacterStats stats : findGlobalList(playerCharacterId))
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
