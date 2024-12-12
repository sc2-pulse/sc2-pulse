// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.MapStats;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
public class MapStatsDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(MapStatsDAO.class);
    
    public static final String STD_SELECT = 
        "map_stats.id AS \"map_stats.id\", "
        + "map_stats.map_id AS \"map_stats.map_id\", "
        + "map_stats.league_id AS \"map_stats.league_id\", "
        + "map_stats.race AS \"map_stats.race\", "
        + "map_stats.versus_race AS \"map_stats.versus_race\", "
        + "map_stats.games AS \"map_stats.games\", "
        + "map_stats.games_with_duration AS \"map_stats.games_with_duration\", "
        + "map_stats.wins AS \"map_stats.wins\", "
        + "map_stats.losses AS \"map_stats.losses\", "
        + "map_stats.ties AS \"map_stats.ties\", "
        + "map_stats.duration AS \"map_stats.duration\" ";

    private static final String UPDATE_STATS_MATCH_FILTER =
        "SELECT DISTINCT(match.id) "
        + "FROM match "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "LEFT JOIN team_state ON match_participant.team_id = team_state.team_id "
            + "AND match_participant.team_state_timestamp = team_state.timestamp "
        + "WHERE date >= :from AND date < :to "
        + "AND type = %7$s "
        + "AND team_state.region_rank IS NOT NULL "
        + "GROUP BY match.id "
        + "HAVING COUNT(*) = 2 "
        + "AND COUNT(team_state.region_rank) = 2 "
        + "AND SUM(decision) = %8$s";

    private static final String UPDATE_STATS_VERSUS_FILTER =
        "SELECT DISTINCT(match.id) "
        + "FROM match_filter "
        + "INNER JOIN match USING(id) "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "INNER JOIN team ON match_participant.team_id = team.id "
        + "INNER JOIN team_member ON team.id = team_member.team_id ";

    private static final String UPDATE_STATS_MATCH_UP_LEAGUE_FILTER =
        "SELECT match.id, "
        + "match_participant.player_character_id, "
        + "get_top_percentage_league_lotv"
        + "("
            + "team_state.region_rank, "
            + "team_state.region_team_count::DOUBLE PRECISION, "
            + "true"
        + ") AS league_type "
        + "FROM versus_race_filter "
        + "INNER JOIN match USING(id) "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "INNER JOIN team ON match_participant.team_id = team.id "
        + "INNER JOIN team_member ON team.id = team_member.team_id "
        + "INNER JOIN team_state ON match_participant.team_id = team_state.team_id "
            + "AND match_participant.team_state_timestamp = team_state.timestamp ";

    private static final String UPDATE_STATS_TEMPLATE_END =
        "matchup_filter AS "
        + "( "
            + "SELECT map_id, "
            + "league.id AS league_id, "
            + "match_participant.decision, "
            + "COUNT(*) AS games, "
            + "COUNT(match.duration) AS games_with_duration, "
            + "SUM(duration) AS duration "
            + "FROM matchup_league_filter "
            + "INNER JOIN match USING(id) "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
                + "AND match_participant.player_character_id = matchup_league_filter.player_character_id "
            + "INNER JOIN team ON match_participant.team_id = team.id "
            + "INNER JOIN season ON team.season = season.battlenet_id "
                + "AND team.region = season.region "
            + "INNER JOIN league ON season.id = league.season_id "
                + "AND league.queue_type = team.queue_type "
                + "AND league.team_type = team.team_type "
                + "AND league.type = matchup_league_filter.league_type "
            + "GROUP BY map_id, league.id, match_participant.decision "
        + "), "
        + "all_filter AS "
        + "( "
            + "SELECT map_id, "
            + "league_id, "
            + "SUM(games) AS games, "
            + "SUM(games_with_duration) AS games_with_duration, "
            + "SUM(duration) AS duration "
            + "FROM matchup_filter "
            + "GROUP BY map_id, league_id "
        + "), "
        + "win_filter AS "
        + "( "
            + "SELECT map_id, league_id, games "
            + "FROM matchup_filter "
            + "WHERE decision = %5$s "
        + "), "
        + "tie_filter AS "
        + "( "
        + " SELECT map_id, league_id, games "
            + "FROM matchup_filter "
            + "WHERE decision = %6$s "
        + "), "
        + "loss_filter AS "
        + "( "
            + "SELECT map_id, league_id, SUM(games) AS games "
            + "FROM matchup_filter "
            + "WHERE decision != %5$s AND decision != %6$s "
            + "GROUP BY map_id, league_id"
        + "), "
        + "vals AS "
        + "( "
            + "SELECT "
            + "map_id, "
            + "league_id, "
            + "%2$s AS race, "
            + "%4$s AS versus_race, "
            + "all_filter.games, "
            + "COALESCE(all_filter.games_with_duration, 0) AS games_with_duration, "
            + "COALESCE(win_filter.games, 0) AS wins, "
            + "COALESCE(tie_filter.games, 0) AS ties, "
            + "COALESCE(loss_filter.games, 0) AS losses, "
            + "COALESCE(all_filter.duration, 0) AS duration "
            + "FROM all_filter "
            + "LEFT JOIN win_filter USING(map_id, league_id) "
            + "LEFT JOIN tie_filter USING(map_id, league_id) "
            + "LEFT JOIN loss_filter USING(map_id, league_id) "

            + "UNION "

            + "SELECT "
            + "null AS map_id, "
            + "league_id, "
            + "%2$s AS race, "
            + "%4$s AS versus_race, "
            + "SUM(all_filter.games) AS games, "
            + "COALESCE(SUM(all_filter.games_with_duration), 0) AS games_with_duration, "
            + "COALESCE(SUM(win_filter.games), 0) AS wins, "
            + "COALESCE(SUM(tie_filter.games), 0) AS ties, "
            + "COALESCE(SUM(loss_filter.games), 0) AS losses, "
            + "COALESCE(SUM(all_filter.duration), 0) AS duration "
            + "FROM all_filter "
            + "LEFT JOIN win_filter USING(map_id, league_id) "
            + "LEFT JOIN tie_filter USING(map_id, league_id) "
            + "LEFT JOIN loss_filter USING(map_id, league_id) "
            + "GROUP BY league_id"
        + "), "
        + "updated AS "
        + "("
            + "UPDATE map_stats "
            + "SET games = map_stats.games + vals.games, "
            + "games_with_duration = map_stats.games_with_duration + vals.games_with_duration, "
            + "wins = map_stats.wins + vals.wins, "
            + "ties = map_stats.ties + vals.ties, "
            + "losses = map_stats.losses + vals.losses, "
            + "duration = map_stats.duration + vals.duration "
            + "FROM vals "
            + "WHERE map_stats.league_id = vals.league_id "
            + "AND COALESCE(map_stats.map_id, -1) = COALESCE(vals.map_id, -1) "
            + "AND map_stats.race = vals.race "
            + "AND map_stats.versus_race = vals.versus_race "
            + "RETURNING id, map_stats.league_id, map_stats.map_id, map_stats.race, map_stats.versus_race "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO map_stats(league_id, map_id, race, versus_race, games, games_with_duration, wins, losses, ties, duration) "
            + "SELECT "
            + "vals.league_id, "
            + "vals.map_id, "
            + "vals.race, "
            + "vals.versus_race, "
            + "vals.games, "
            + "vals.games_with_duration, "
            + "vals.wins, "
            + "vals.losses, "
            + "vals.ties, "
            + "vals.duration "
            + "FROM vals "
            + "LEFT JOIN updated ON vals.league_id = updated.league_id "
                + "AND COALESCE(vals.map_id, -1) = COALESCE(updated.map_id, -1) "
                + "AND vals.race = updated.race "
                + "AND vals.versus_race = updated.versus_race "
            + "WHERE updated.id IS NULL "
            + "RETURNING id, map_stats.league_id, map_stats.map_id, map_stats.race, map_stats.versus_race "
        + ") "
        + "SELECT COUNT(*) FROM updated "
        + "UNION "
        + "SELECT COUNT(*) FROM inserted";

    private static final String UPDATE_VERSUS_STATS_TEMPLATE =
        "WITH match_filter AS(" + UPDATE_STATS_MATCH_FILTER + "), "
        + "versus_race_filter AS "
        + "( "
            + UPDATE_STATS_VERSUS_FILTER
            + "WHERE team_member.%3$s_games_played > 0 "
        + "), "
        + "matchup_league_filter AS "
        + "( "
            + UPDATE_STATS_MATCH_UP_LEAGUE_FILTER
            + "WHERE team_member.%1$s_games_played > 0 "
        + "), "
        + UPDATE_STATS_TEMPLATE_END;

    private static final String UPDATE_MIRROR_STATS_TEMPLATE =
        "WITH match_filter AS(" + UPDATE_STATS_MATCH_FILTER + "), "
        + "versus_race_filter AS "
        + "( "
            + UPDATE_STATS_VERSUS_FILTER
            + "WHERE team_member.%3$s_games_played > 0 "
            + "GROUP BY match.id "
            + "HAVING COUNT(*) = 2"
        + "), "
        + "matchup_league_filter AS( " + UPDATE_STATS_MATCH_UP_LEAGUE_FILTER + "), "
        + UPDATE_STATS_TEMPLATE_END;

    private static final String FIND_BY_IDS_QUERY =
        "SELECT " + STD_SELECT
        + "FROM map_stats "
        + "WHERE COALESCE(map_id, -1) = COALESCE(:mapId, -1) "
        + "AND league_id IN(:leagueIds)";


    private static List<String> UPDATE_STATS_QUERIES;

    private static RowMapper<MapStats> STD_ROW_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public MapStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
        UPDATE_STATS_QUERIES = initUpdateQueries(conversionService);
    }
    
    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new MapStats
        (
            rs.getInt("map_stats.id"),
            rs.getInt("map_stats.league_id"),
            DAOUtils.getInteger(rs, "map_stats.map_id"),
            conversionService.convert(rs.getInt("map_stats.race"), Race.class),
            conversionService.convert(rs.getInt("map_stats.versus_race"), Race.class),
            rs.getInt("map_stats.games"),
            rs.getInt("map_stats.games_with_duration"),
            rs.getInt("map_stats.wins"),
            rs.getInt("map_stats.losses"),
            rs.getInt("map_stats.ties"),
            rs.getInt("map_stats.duration")
        );
    }

    private static List<String> initUpdateQueries(ConversionService conversionService)
    {
        Integer win = conversionService.convert(BaseMatch.Decision.WIN, Integer.class);
        Integer tie = conversionService.convert(BaseMatch.Decision.TIE, Integer.class);
        Integer loss = conversionService.convert(BaseMatch.Decision.LOSS, Integer.class);
        Integer winPlusLoss = win + loss;
        String type = String.valueOf(conversionService.convert(BaseMatch.MatchType._1V1, Integer.class));

        List<String> queries = new ArrayList<>();
        for(Race race : Race.values())
        {
            for(Race versusRace : Race.values())
            {
                if(race == versusRace)
                {
                    queries.add(String.format(UPDATE_MIRROR_STATS_TEMPLATE,
                        race.getName().toLowerCase(), conversionService.convert(race, Integer.class),
                        versusRace.getName().toLowerCase(), conversionService.convert(versusRace, Integer.class),
                        win, tie, type, winPlusLoss));
                }
                else
                {
                    queries.add(String.format(UPDATE_VERSUS_STATS_TEMPLATE,
                        race.getName().toLowerCase(), conversionService.convert(race, Integer.class),
                        versusRace.getName().toLowerCase(), conversionService.convert(versusRace, Integer.class),
                        win, tie, type, winPlusLoss));
                }
            }
        }
        return queries;
    }
    
    public static RowMapper<MapStats> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    @Transactional
    public void add(OffsetDateTime from, OffsetDateTime to)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("to", to);
        UPDATE_STATS_QUERIES.forEach(q->template.query(q, params, DAOUtils.INT_EXTRACTOR));
        LOG.debug("Added map stats {}-{}", from, to);
    }

    public List<MapStats> find(Integer mapId, List<Integer> leagueIds)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("mapId", mapId)
            .addValue("leagueIds", leagueIds);
        return template.query(FIND_BY_IDS_QUERY, params, STD_ROW_MAPPER);
    }
    
}
