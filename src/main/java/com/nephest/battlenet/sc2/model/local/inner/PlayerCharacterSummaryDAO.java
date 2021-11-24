// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.Race;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PlayerCharacterSummaryDAO
{

    public static final String STD_SELECT =
        "player_character_summary.player_character_id AS \"player_character_summary.player_character_id\", "
        + "player_character_summary.race AS \"player_character_summary.race\", "
        + "player_character_summary.games AS \"player_character_summary.games\", "
        + "player_character_summary.rating_avg AS \"player_character_summary.rating_avg\", "
        + "player_character_summary.rating_max AS \"player_character_summary.rating_max\", "
        + "player_character_summary.rating_cur AS \"player_character_summary.rating_cur\" ";

    private static final String FIND_PLAYER_CHARACTER_SUMMARY_BY_IDS_AND_TIMESTAMP =
        "SELECT " + STD_SELECT + " FROM get_player_character_summary(:ids, :from) player_character_summary";

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
            rs.getInt("player_character_summary.rating_cur")
        );
    }

    public static RowMapper<PlayerCharacterSummary> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public List<PlayerCharacterSummary> find(Long[] ids, OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids, Types.ARRAY)
            .addValue("from", from);
        return template.query(FIND_PLAYER_CHARACTER_SUMMARY_BY_IDS_AND_TIMESTAMP, params, STD_ROW_MAPPER);
    }

}
