// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
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
    "WITH "
    + "player_character_filtered AS "
    + "( "
        + "SELECT player_character.id "
        + "FROM player_character "
        + "%1$s"
    + "), "
    + "player_character_stats_filtered AS "
    + "( "
        + "SELECT DISTINCT ON(player_character_stats.player_character_id) "
        + "player_character_stats.id, player_character_stats.player_character_id "
        + "FROM player_character_filtered "
        + "INNER JOIN player_character_stats ON player_character_stats.player_character_id = player_character_filtered.id "
        + "WHERE COALESCE(player_character_stats.season_id, -32768) = -32768 "
        + "ORDER BY player_character_stats.player_character_id, player_character_stats.rating_max DESC, "
        + "player_character_stats.race, " //prevent summary selection where racial stats are available
        + "player_character_stats.games_played DESC, player_character_stats.league_max DESC, "
        + "player_character_stats.queue_type, player_character_stats.team_type "
    + ") "

    + "SELECT "
    + "account.id AS \"account.id\", "
    + "account.partition AS \"account.partition\", "
    + "account.battle_tag AS \"account.battle_tag\", "
    + "player_character_stats.player_character_id, "
    + "player_character.region AS \"player_character.region\", "
    + "player_character.battlenet_id AS \"player_character.battlenet_id\", "
    + "player_character.realm AS \"player_character.realm\", "
    + "player_character.name AS \"player_character.name\", "
    + "player_character_stats.race AS \"race\", "
    + "player_character_stats.league_max AS \"league_max\", "
    + "player_character_stats.rating_max AS \"rating_max\", "
    + "player_character_stats.games_played AS \"games_played\" "

    + "FROM player_character_stats_filtered "
    + "INNER JOIN player_character ON player_character.id = player_character_stats_filtered.player_character_id "
    + "INNER JOIN account ON player_character.account_id = account.id "
    + "INNER JOIN player_character_stats ON player_character_stats_filtered.id = player_character_stats.id "

    + "ORDER BY rating_max DESC";

    private static final String FIND_DISTINCT_CHARACTER_BY_NAME_OR_BATTLE_TAG_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "WHERE LOWER(player_character.name) LIKE LOWER(:name) "
        + "UNION "
        + "SELECT player_character.id "
        + "FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "WHERE LOWER(account.battle_tag) LIKE LOWER(:name) "
    );
    private static final String FIND_DISTINCT_CHARACTER_BY_FULL_BATTLE_TAG_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "INNER JOIN account ON player_character.account_id = account.id "
        + "WHERE account.battle_tag = :battleTag "
    );
    private static final String FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "INNER JOIN account ON player_character.account_id=account.id "
        + "WHERE account.id = :accountId "
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final AccountDAO accountDAO;

    private final RowMapper<LadderDistinctCharacter> DISTINCT_CHARACTER_ROW_MAPPER;

    @Autowired
    public LadderCharacterDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        @Autowired AccountDAO accountDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.accountDAO = accountDAO;
        DISTINCT_CHARACTER_ROW_MAPPER =
        (rs, num)->
        {
            Integer gamesPlayed = rs.getInt("games_played");
            int raceInt = rs.getInt("race");
            Race race = rs.wasNull() ? null : conversionService.convert(raceInt, Race.class);
            return new LadderDistinctCharacter
                (
                    conversionService.convert(rs.getInt("league_max"), League.LeagueType.class),
                    rs.getInt("rating_max"),
                    accountDAO.getStdRowMapper().mapRow(rs, num),
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
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByName(String name)
    {
        boolean isBattleTag = name.contains("#");
        if(isBattleTag) return findDistinctCharactersByFullBattleTag(name);
        return findDistinctCharactersByNameOrBattletag(name);
    }

    private List<LadderDistinctCharacter> findDistinctCharactersByNameOrBattletag(String name)
    {
        name = PostgreSQLUtils.escapeLikePattern(name) + "#%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_NAME_OR_BATTLE_TAG_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

    private List<LadderDistinctCharacter> findDistinctCharactersByFullBattleTag(String battleTag)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("battleTag", battleTag);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_FULL_BATTLE_TAG_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByAccountId(Long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

}
