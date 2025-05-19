// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Validated
@Repository
public class PlayerCharacterLinkDAO
{

    public static final String STD_SELECT =
        "player_character_link.player_character_id AS \"player_character_link.player_character_id\", "
        + "player_character_link.type AS \"player_character_link.type\", "
        + "player_character_link.url AS \"player_character_link.url\" ";

    private static final String FIND_BY_CHARACTER_IDS =
        "SELECT " + STD_SELECT
        + "FROM player_character_link "
        + "WHERE player_character_id IN (:playerCharacterIds) "
        + "ORDER BY player_character_id, type";

    private static final String FIND_BY_CHARACTER_TYPE_AND_URL =
        "SELECT " + STD_SELECT
        + "FROM player_character_link "
        + "WHERE type = :type "
        + "AND url = :url "
        + "ORDER BY type, url";

    private static final String MERGE =
        "WITH "
        + "vals AS (VALUES :links), "
        + "lock_filter AS "
        + "("
            + "SELECT player_character_id, type, v.url "
            + "FROM vals v(player_character_id, type, url) "
            + "INNER JOIN player_character_link USING(player_character_id, type) "
            + "ORDER BY player_character_id, type "
            + "FOR NO KEY UPDATE "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE player_character_link "
            + "SET url = v.url "
            + "FROM lock_filter v "
            + "WHERE player_character_link.player_character_id = v.player_character_id "
            + "AND player_character_link.type = v.type "
            + "RETURNING player_character_link.player_character_id, player_character_link.type "
        + ") "
        + "INSERT INTO player_character_link(player_character_id, type, url) "
        + "SELECT v.player_character_id, v.type, v.url "
        + "FROM vals v(player_character_id, type, url) "
        + "LEFT JOIN updated USING(player_character_id, type) "
        + "WHERE updated.player_character_id IS NULL "
        + "ON CONFLICT(player_character_id, type) DO UPDATE "
        + "SET url = excluded.url";

    private static RowMapper<PlayerCharacterLink> STD_ROW_MAPPER;
    private static ResultSetExtractor<PlayerCharacterLink> STD_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public PlayerCharacterLinkDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new PlayerCharacterLink
        (
            rs.getLong("player_character_link.player_character_id"),
            conversionService.convert(rs.getInt("player_character_link.type"), SocialMedia.class),
            rs.getString("player_character_link.url")
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<PlayerCharacterLink> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<PlayerCharacterLink> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public int merge(@Valid Set<@Valid PlayerCharacterLink> links)
    {
        if(links.isEmpty()) return 0;

        List<Object[]> data = links.stream()
            .map(link->new Object[]
            {
                link.getPlayerCharacterId(),
                conversionService.convert(link.getType(), Integer.class),
                link.getRelativeUrl()
            })
            .collect(Collectors.toList());
        SqlParameterSource params = new MapSqlParameterSource().addValue("links", data);
        return template.update(MERGE, params);
    }

    public List<PlayerCharacterLink> find(Set<Long> playerCharacterIds)
    {
        if(playerCharacterIds.isEmpty()) return List.of();

        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", playerCharacterIds);
        return template.query(FIND_BY_CHARACTER_IDS, params, STD_ROW_MAPPER);
    }

    public List<PlayerCharacterLink> find(SocialMedia type, String url)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("type", conversionService.convert(type, Integer.class))
            .addValue("url", url);
        return template.query(FIND_BY_CHARACTER_TYPE_AND_URL, params, STD_ROW_MAPPER);
    }

}
