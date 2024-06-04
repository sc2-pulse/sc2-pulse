// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec.FRAME_DURATION_UNIT;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.MapStatsFrame;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MapStatsFilmFrameDAO
{

    public static final QueueType QUEUE = QueueType.LOTV_1V1;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final List<BaseLeague.LeagueType> LEAGUES = List.of
    (
        BaseLeague.LeagueType.MASTER,
        BaseLeague.LeagueType.DIAMOND,
        BaseLeague.LeagueType.PLATINUM,
        BaseLeague.LeagueType.GOLD,
        BaseLeague.LeagueType.SILVER,
        BaseLeague.LeagueType.BRONZE
    );


    public static final String STD_SELECT =
        "map_stats_film_frame.map_stats_film_id AS \"map_stats_film_frame.map_stats_film_id\", "
        + "map_stats_film_frame.number AS \"map_stats_film_frame.number\", "
        + "map_stats_film_frame.wins AS \"map_stats_film_frame.wins\", "
        + "map_stats_film_frame.games AS \"map_stats_film_frame.games\" ";

    private static final String ADD =
        "WITH match_group AS\n"
        + "(\n"
            + "SELECT MAX(map_id) AS map_id,\n"
            + "MAX(match.region) AS region,\n"
            + "MAX(team_member.team_season) AS season,\n"
            + "array_agg(get_favorite_race("
                + "terran_games_played, "
                + "protoss_games_played, "
                + "zerg_games_played, "
                + "random_games_played) ORDER BY 1 DESC)::smallint[] AS matchup,\n"
            + "array_agg(get_top_percentage_league_tier_lotv("
                + "region_rank, region_team_count, false))::league_tier_type[] AS league_tier,\n"
            + "MAX(duration) / :frameDuration AS duration_ix,\n"
            + "array_agg(get_favorite_race("
                + "terran_games_played, "
                + "protoss_games_played, "
                + "zerg_games_played, "
                + "random_games_played) ORDER BY decision) AS decision\n"
            + "FROM match\n"
            + "INNER JOIN match_participant ON match.id = match_participant.match_id\n"
            + "INNER JOIN team_state ON match_participant.team_id = team_state.team_id\n"
                + "AND match_participant.team_state_timestamp = team_state.timestamp\n"
            + "INNER JOIN population_state ON team_state.population_state_id = population_state.id\n"
            + "INNER JOIN team_member ON team_member.team_id = match_participant.team_id\n"
                + "AND team_member.player_character_id = match_participant.player_character_id\n"
            + "WHERE date >= :from AND date < :to\n"
            + "AND type = :matchType\n"
            + "GROUP BY match_id\n"
            + "HAVING COUNT(*) = :matchParticipantCount\n"
            + "AND SUM(decision) = :decisionSum\n"
        + "),\n"
        + "match_group_filter AS\n"
        + "(\n"
            + "SELECT map_id,\n"
            + "CASE\n"
                + "WHEN matchup::integer[] @> ARRAY[1, 3]::integer[]\n"
                + "THEN ARRAY[1, 3]::smallint[]\n"
                + "ELSE (ARRAY(SELECT unnest(matchup) ORDER BY 1 DESC))::smallint[]\n"
            + "END AS matchup,\n"
            + "region,\n"
            + "season,\n"
            + "league_tier,\n"
            + "duration_ix,\n"
            + "decision\n"
            + "FROM match_group\n"
            + "WHERE matchup[1] != matchup[2]\n"
        + "),\n"
        + "stats_group AS\n"
        + "(\n"
            + "SELECT map_id, matchup, region, season,\n"
            + "(league_tier[1]).league, (league_tier[1]).tier,\n"
            + "duration_ix,\n"
            + "COUNT(*) as games,\n"
            + "COUNT(*) FILTER(WHERE decision[1] = matchup[1]) as wins,\n"
            + "false AS cross_tier\n"
            + "FROM match_group_filter\n"
            + "WHERE (league_tier[1]).id = (league_tier[2]).id\n"
            + "GROUP BY map_id, matchup, region, season, league_tier[1], duration_ix\n"

            + "UNION\n"

            + "SELECT map_id, matchup, region, season,\n"
            + "(league_tier[1]).league, (league_tier[1]).tier,\n"
            + "duration_ix,\n"
            + "COUNT(*) as games,\n"
            + "COUNT(*) FILTER(WHERE decision[1] = matchup[1]) as wins,\n"
            + "true AS cross_tier\n"
            + "FROM match_group_filter\n"
            + "WHERE (league_tier[1]).id != (league_tier[2]).id\n"
            + "GROUP BY map_id, matchup, region, season, league_tier[1], duration_ix\n"

            + "UNION\n"

            + "SELECT map_id, matchup, region, season,\n"
            + "(league_tier[2]).league, (league_tier[2]).tier,\n"
            + "duration_ix,\n"
            + "COUNT(*) as games,\n"
            + "COUNT(*) FILTER(WHERE decision[1] = matchup[1]) as wins,\n"
            + "true AS cross_tier\n"
            + "FROM match_group_filter\n"
            + "WHERE (league_tier[1]).id != (league_tier[2]).id\n"
            + "GROUP BY map_id, matchup, region, season, league_tier[2], duration_ix\n"
        + "),\n"
        + "film_group AS\n"
        + "(\n"
            + "SELECT map_id,\n"
            + "map_stats_film_spec.id AS map_stats_film_spec_id,\n"
            + "league_tier.id AS league_tier_id,\n"
            + "duration_ix,\n"
            + "games,\n"
            + "wins,\n"
            + "cross_tier\n"
            + "FROM stats_group\n"
            + "INNER JOIN season ON stats_group.region = season.region\n"
                + "AND stats_group.season = season.battlenet_id\n"
            + "INNER JOIN league ON league.season_id = season.id\n"
                + "AND stats_group.league = league.type\n"
                + "AND league.queue_type = :queueType\n"
                + "AND league.team_type = :teamType\n"
            + "INNER JOIN league_tier ON league.id = league_tier.league_id\n"
                + "AND league_tier.type = stats_group.tier\n"
            + "INNER JOIN map_stats_film_spec ON stats_group.matchup[1] = map_stats_film_spec.race\n"
                + "AND stats_group.matchup[2] = map_stats_film_spec.versus_race\n"
                + "AND map_stats_film_spec.frame_duration = :frameDuration\n"
        + "),\n"
        + "existing_film_group AS\n"
        + "(\n"
            + "SELECT id AS map_stats_film_id,\n"
            + "film_group.*\n"
            + "FROM film_group\n"
            + "INNER JOIN map_stats_film USING"
            + "("
                + "map_id, "
                + "league_tier_id, "
                + "map_stats_film_spec_id, "
                + "cross_tier "
            + ")\n"
        + "),\n"
        + "new_film AS\n"
        + "(\n"
            + "INSERT INTO map_stats_film"
            + "("
                + "map_id, "
                + "league_tier_id, "
                + "map_stats_film_spec_id, "
                + "cross_tier "
            + ")\n"
            + "SELECT DISTINCT ON(map_id, league_tier_id, map_stats_film_spec_id, cross_tier)\n"
            + "map_id, league_tier_id, map_stats_film_spec_id, cross_tier\n"
            + "FROM film_group\n"
            + "WHERE NOT EXISTS\n"
            + "(\n"
                + "SELECT 1\n"
                + "FROM existing_film_group\n"
                + "WHERE film_group.map_id = existing_film_group.map_id\n"
                + "AND film_group.league_tier_id = existing_film_group.league_tier_id\n"
                + "AND film_group.map_stats_film_spec_id = existing_film_group.map_stats_film_spec_id\n"
                + "AND film_group.cross_tier = existing_film_group.cross_tier\n"
            + ")\n"
            + "RETURNING *\n"
        + "),\n"
        + "new_film_group AS\n"
        + "(\n"
            + "SELECT id AS map_stats_film_id,\n"
            + "film_group.*\n"
            + "FROM film_group\n"
            + "INNER JOIN new_film USING"
            + "("
                + "map_id, "
                + "league_tier_id, "
                + "map_stats_film_spec_id, "
                + "cross_tier "
            + ")\n"
        + "),\n"
        + "all_film_group AS\n"
        + "(\n"
            + "SELECT * FROM existing_film_group\n"
            + "UNION\n"
            + "SELECT * FROM new_film_group\n"
        + "),\n"
        + "updated_frame AS\n"
        + "(\n"
            + "UPDATE map_stats_film_frame\n"
            + "SET games = map_stats_film_frame.games + all_film_group.games,\n"
            + "wins = map_stats_film_frame.wins + all_film_group.wins\n"
            + "FROM all_film_group\n"
            + "WHERE map_stats_film_frame.map_stats_film_id = all_film_group.map_stats_film_id\n"
            + "AND map_stats_film_frame.number IS NOT DISTINCT FROM all_film_group.duration_ix\n"
            + "RETURNING map_stats_film_frame.map_stats_film_id, number\n"
        + ")\n"
        + "INSERT INTO map_stats_film_frame(map_stats_film_id, number, wins, games)\n"
        + "SELECT map_stats_film_id, duration_ix, wins, games\n"
        + "FROM all_film_group\n"
        + "WHERE NOT EXISTS\n"
        + "(\n"
            + "SELECT 1\n"
            + "FROM updated_frame\n"
            + "WHERE updated_frame.map_stats_film_id = all_film_group.map_stats_film_id\n"
            + "AND updated_frame.number IS NOT DISTINCT FROM all_film_group.duration_ix\n"
        + ")";

    private static final String FIND_BY_FILM_IDS =
        "SELECT " + STD_SELECT
        + "FROM map_stats_film_frame "
        + "WHERE map_stats_film_id IN (:mapStatsFilmIds) "
        + "AND "
        + "("
            + ":numberMax::integer IS NULL "
            + "OR (number is NULL OR number <= :numberMax::integer) "
        + ")";

    public static final RowMapper<MapStatsFrame> STD_MAPPER = (rs, i)->new MapStatsFrame
    (
        rs.getInt("map_stats_film_frame.map_stats_film_id"),
        DAOUtils.getInteger(rs, "map_stats_film_frame.number"),
        rs.getInt("map_stats_film_frame.wins"),
        rs.getInt("map_stats_film_frame.games")
    );
    public static final ResultSetExtractor<MapStatsFrame> STD_EXTRACTOR
        = DAOUtils.getResultSetExtractor(STD_MAPPER);

    private final int decisionSum;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public MapStatsFilmFrameDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        decisionSum = conversionService.convert(BaseMatch.Decision.WIN, Integer.class)
            + conversionService.convert(BaseMatch.Decision.LOSS, Integer.class);
    }

    public int add(OffsetDateTime from, OffsetDateTime to, Duration frameDuration)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchType", conversionService
                .convert(BaseMatch.MatchType._1V1, Integer.class))
            .addValue("matchParticipantCount", QUEUE.getTeamFormat().getMemberCount(TEAM_TYPE) * 2)
            .addValue("decisionSum", decisionSum)
            .addValue("queueType", conversionService.convert(QUEUE, Integer.class))
            .addValue("teamType", conversionService.convert(TEAM_TYPE, Integer.class))

            .addValue("frameDuration", frameDuration.get(FRAME_DURATION_UNIT))
            .addValue("from", from)
            .addValue("to", to);
        return template.update(ADD, params);
    }

    public List<MapStatsFrame> find(Set<Integer> mapStatsFilmIds, Integer numberMax)
    {
        if(mapStatsFilmIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("mapStatsFilmIds", mapStatsFilmIds)
            .addValue("numberMax", numberMax, Types.INTEGER);
        return template.query(FIND_BY_FILM_IDS, params, STD_MAPPER);
    }

}
