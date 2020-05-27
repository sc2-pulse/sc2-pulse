// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LadderCharacterDAO
{

    private static final String FIND_DISTINCT_CHARACTER_FORMAT =
        "SELECT "
        + "MAX(account.id) AS \"account.id\", "
        + "MAX(account.battle_tag) AS \"account.battle_tag\", "
        + "player_character_stats.player_character_id, "
        + "MAX(player_character.region) AS \"player_character.region\", "
        + "MAX(player_character.battlenet_id) AS \"player_character.battlenet_id\", "
        + "MAX(player_character.realm) AS \"player_character.realm\", "
        + "MAX(player_character.name) AS \"player_character.name\", "
        + "MAX(player_character_stats.race) AS \"race\", "
        + "MAX(player_character_stats.league_max) AS \"league_max\", "
        + "MAX(player_character_stats.rating_max) AS \"rating_max\", "
        + "MAX(player_character_stats.games_played) AS \"games_played\" "

        + "FROM player_character_stats "
        + "INNER JOIN "
        + "("
        + "SELECT MAX(player_character_stats.rating_max) AS rating_max_global "
        + "FROM player_character_stats "
        + "INNER JOIN player_character "
        + " ON player_character_stats.player_character_id=player_character.id "
        + "%2$s "
        + "AND COALESCE(player_character_stats.season_id, -32768) = -32768 "
        + "GROUP BY player_character_stats.player_character_id "
        + ") "
        + "player_character_stats_max ON player_character_stats.rating_max=player_character_stats_max.rating_max_global "
        + "INNER JOIN player_character ON player_character_stats.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "

        + "%1$s "
        + "AND COALESCE(player_character_stats.season_id, -32768) = -32768 "

        + "GROUP BY player_character_stats.player_character_id "

        + "ORDER BY rating_max DESC";

    private static final String FIND_DISTINCT_CHARACTER_BY_NAME_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "WHERE LOWER(player_character.name) LIKE LOWER(:name) ",
        "WHERE LOWER(player_character.name) LIKE LOWER(:name) "
    );
    private static final String FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "WHERE account.id = :accountId ",
        "INNER JOIN account ON player_character.account_id=account.id "
            + "WHERE account.id = :accountId "
    );

    private final NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    private final RowMapper<LadderDistinctCharacter> DISTINCT_CHARACTER_ROW_MAPPER =
    (rs, num)->
    {
        Integer gamesPlayed = rs.getInt("games_played");
        int raceInt = rs.getInt("race");
        Race race = rs.wasNull() ? null : conversionService.convert(raceInt, Race.class);
        return new LadderDistinctCharacter
            (
                conversionService.convert(rs.getInt("league_max"), League.LeagueType.class),
                rs.getInt("rating_max"),
                new Account(rs.getLong("account.id"), rs.getString("account.battle_tag")),
                new PlayerCharacter
                    (
                        rs.getLong("player_character_id"),
                        rs.getLong("account.id"),
                        conversionService.convert(rs.getInt("player_character.region"), Region.class),
                        rs.getLong("player_character.battlenet_id"),
                        rs.getInt("player_character.realm"),
                        rs.getString("player_character.name")
                    ),
                race == Race.TERRAN ? gamesPlayed : null,
                race == Race.PROTOSS ? gamesPlayed : null,
                race == Race.ZERG ? gamesPlayed : null,
                race == Race.RANDOM ? gamesPlayed : null,
                gamesPlayed
            );
    };

    @Autowired
    public LadderCharacterDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByName(String name)
    {
        name = PostgreSQLUtils.escapeLikePattern(name) + "#%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_NAME_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByAccountId(Long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

}
