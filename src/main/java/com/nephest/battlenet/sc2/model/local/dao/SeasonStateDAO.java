// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.SeasonState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class SeasonStateDAO
{

    public static final String STD_SELECT =
        "season_state.season_id AS \"season_state.season_id\", "
        + "season_state.timestamp AS \"season_state.timestamp\", "
        + "season_state.player_count AS \"season_state.player_count\", "
        + "season_state.total_games_played AS \"season_state.total_games_played\", "
        + "season_state.games_played AS \"season_state.games_played\" ";

    private static final String MERGE_QUERY =
        "WITH team_filter AS "
        + "( "
            + "SELECT DISTINCT team_id "
            + "FROM team_state "
            + "WHERE \"timestamp\" >= :point - INTERVAL '1 HOURS' "
            + "AND \"timestamp\" < :point "
        + "), "
        + "account_filter AS "
        + "( "
            + "SELECT season.id, COUNT(DISTINCT(player_character.account_id)) AS player_count "
            + "FROM team_filter "
            + "INNER JOIN team_member USING(team_id) "
            + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
            + "INNER JOIN team ON team_filter.team_id = team.id "
            + "INNER JOIN season ON team.season = season.battlenet_id "
                + "AND team.region = season.region "
            + "GROUP BY season.id "
        + "), "
        + "total AS ("
            + "SELECT season.id, SUM(wins + losses + ties) AS \"total_games_played\" "
            + "FROM team "
            + "INNER JOIN season ON team.season = season.battlenet_id "
                + "AND team.region = season.region "
            + "WHERE team.season = :season "
            + "GROUP BY season.id"
        + "), "
        + "prev AS ("
            + "SELECT total.id, season_state.total_games_played "
            + "FROM total "
            + "INNER JOIN season_state ON total.id = season_state.season_id "
            + "WHERE \"timestamp\" = :point - INTERVAL '1 HOURS'"
        + "), "
        + "fin AS ("
            + "SELECT id, account_filter.player_count, total.total_games_played, "
            + "prev.total_games_played AS \"prev_total_games_played\" "
            + "FROM account_filter "
            + "INNER JOIN total USING(id) "
            + "LEFT OUTER JOIN prev USING(id)"
        + ") "
        + "INSERT INTO season_state (season_id, \"timestamp\", player_count, total_games_played, games_played) "
        + "SELECT fin.id, :point, fin.player_count, fin.total_games_played, fin.total_games_played - fin.prev_total_games_played "
        + "FROM fin "
        + "ON CONFLICT(\"timestamp\", season_id) DO UPDATE SET "
        + "player_count = excluded.player_count, "
        + "total_games_played = excluded.total_games_played, "
        + "games_played = excluded.games_played";


    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<SeasonState> STD_ROW_MAPPER = (rs, num)->
    {
        int g = rs.getInt("season_state.games_played");
        Integer games = rs.wasNull() ? null : g;
        return new SeasonState
        (
            rs.getInt("season_state.season_id"),
            rs.getObject("season_state.timestamp", OffsetDateTime.class),
            rs.getInt("season_state.player_count"),
            rs.getInt("season_state.total_games_played"),
            games
        );
    };


    @Autowired
    public SeasonStateDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }

    public int merge(OffsetDateTime point, int season)
    {
        point = point.withMinute(0).withSecond(0).withNano(0);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("point", point)
            .addValue("season", season);
        return template.update(MERGE_QUERY, params);
    }

}
