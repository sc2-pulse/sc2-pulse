// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PlayerCharacterSummaryDAO
{

    public static final String STD_SELECT =
        "player_character_summary.player_character_id AS \"player_character_summary.player_character_id\", "
        + "player_character_summary.race AS \"player_character_summary.race\", "
        + "player_character_summary.games AS \"player_character_summary.games\", "
        + "player_character_summary.rating_avg AS \"player_character_summary.rating_avg\", "
        + "player_character_summary.rating_max AS \"player_character_summary.rating_max\", "
        + "player_character_summary.rating_last AS \"player_character_summary.rating_last\", "
        + "player_character_summary.league_type_last AS \"player_character_summary.league_type_last\", "
        + "player_character_summary.global_rank_last AS \"player_character_summary.global_rank_last\" ";

    private static final String FIND_PLAYER_CHARACTER_SUMMARY_BY_IDS_AND_TIMESTAMP =
        "WITH team_filter AS "
        + "( "
            + "SELECT "
            + "team.season, "
            + "team.league_type, "
            + "team.global_rank, "
            + "team.legacy_id, "
            + "team_member.player_character_id, "
            + "team.wins + team.losses + team.ties AS games, "
            + "team.rating, "
            + "(season.end + INTERVAL '7 days')::timestamp AS \"timestamp\" "
            + "FROM team "
            + "INNER JOIN team_member ON team.id = team_member.team_id "
            + "INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id "
            + "WHERE player_character_id IN(:characters) "
            + "AND team.queue_type = 201 "
            + "AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint IN(:races) "
            + "AND season.end >= :from "
        + "), "
        + "team_state_filter AS "
        + "( "
            + "SELECT "
            + "team.season, "
            + "null::INTEGER AS league_type, "
            + "null::INTEGER AS global_rank, "
            + "team.legacy_id, "
            + "team_member.player_character_id, "
            + "team_state.games, "
            + "team_state.rating, "
            + "team_state.timestamp "
            + "FROM team_state "
            + "INNER JOIN team ON team_state.team_id = team.id "
            + "INNER JOIN team_member ON team.id = team_member.team_id "
            + "WHERE player_character_id IN(:characters) "
            + "AND team.queue_type = 201 "
            + "AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint IN(:races) "
            + "AND team_state.timestamp >= :from "
        + "), "
        + "all_union AS "
        + "( "
            + "SELECT t.*, "
            + "lag(t.games) "
                + "OVER (PARTITION BY player_character_id, legacy_id "
                    + "ORDER BY player_character_id ASC, legacy_id ASC, season ASC, timestamp ASC) AS games_prev, "
            + "lag(t.season)"
                + " OVER (PARTITION BY player_character_id, legacy_id "
                    + "ORDER BY player_character_id ASC, legacy_id ASC, season ASC, timestamp ASC) AS season_prev "
            + "FROM "
            + "( "
                + "SELECT * FROM team_filter "
                + "UNION ALL "
                + "SELECT * FROM team_state_filter "
            + ") t "
        + "), "
        + "all_unwrap AS "
        + "( "
            + "SELECT all_union.*, "
            + "CASE "
                + "WHEN all_union.games_prev IS NULL THEN 1 "
                + "WHEN all_union.season <> all_union.season_prev THEN all_union.games "
                + "WHEN all_union.games < all_union.games_prev THEN all_union.games "
                + "ELSE all_union.games - all_union.games_prev "
            + "END AS games_diff "
            + "FROM all_union "
        + "), "
        + "last_value AS "
        + "( "
            + "SELECT DISTINCT ON (player_character_id, legacy_id) "
            + "player_character_id, legacy_id, rating, league_type, global_rank "
            + "FROM all_unwrap "
            + "ORDER BY player_character_id DESC, legacy_id DESC, season DESC, timestamp DESC "
        + ") "
        + "SELECT player_character_id AS \"player_character_summary.player_character_id\", "
        + "substring(legacy_id::text, char_length(legacy_id::text))::smallint AS \"player_character_summary.race\", "
        + "SUM(all_unwrap.games_diff)::smallint AS \"player_character_summary.games\", "
        + "AVG(all_unwrap.rating)::smallint AS \"player_character_summary.rating_avg\", "
        + "MAX(all_unwrap.rating)::smallint AS \"player_character_summary.rating_max\", "
        + "MAX(last_value.rating)::smallint AS \"player_character_summary.rating_last\", "
        + "MAX(last_value.league_type)::smallint AS \"player_character_summary.league_type_last\", "
        + "MAX(last_value.global_rank) AS \"player_character_summary.global_rank_last\" "
        + "FROM all_unwrap "
        + "INNER JOIN last_value USING(player_character_id, legacy_id) "
        + "GROUP BY player_character_id, legacy_id";
    private final List<Integer> DEFAULT_RACES;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<PlayerCharacterSummary> STD_ROW_MAPPER;

    @Autowired
    public PlayerCharacterSummaryDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
        DEFAULT_RACES = Arrays.stream(Race.values())
            .map(r->conversionService.convert(r, Integer.class))
            .collect(Collectors.toList());
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new PlayerCharacterSummary
        (
            rs.getLong("player_character_summary.player_character_id"),
            conversionService.convert(rs.getInt("player_character_summary.race"), Race.class),
            rs.getInt("player_character_summary.games"),
            rs.getInt("player_character_summary.rating_avg"),
            rs.getInt("player_character_summary.rating_max"),
            rs.getInt("player_character_summary.rating_last"),
            conversionService.convert(rs.getInt("player_character_summary.league_type_last"), BaseLeague.LeagueType.class),
            DAOUtils.getInteger(rs, "player_character_summary.global_rank_last")
        );
    }

    public static RowMapper<PlayerCharacterSummary> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public List<PlayerCharacterSummary> find(List<Long> ids, OffsetDateTime from, Race... races)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("characters", ids)
            .addValue("from", from);
        List<Integer> raceInts = races.length == 0
            ? DEFAULT_RACES
            : Arrays.stream(races)
                .map(r->conversionService.convert(r, Integer.class))
                .collect(Collectors.toList());
        params.addValue("races", raceInts);
        return template.query(FIND_PLAYER_CHARACTER_SUMMARY_BY_IDS_AND_TIMESTAMP, params, STD_ROW_MAPPER);
    }

}
