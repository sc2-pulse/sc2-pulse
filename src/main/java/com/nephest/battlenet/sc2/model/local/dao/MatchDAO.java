// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.Match;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class MatchDAO
extends StandardDAO
{

    public static final String STD_SELECT =
        "match.id AS \"match.id\", "
        + "match.date AS \"match.date\", "
        + "match.type AS \"match.type\", "
        + "match.map AS \"match.map\", "
        + "match.updated AS \"match.updated\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO match (date, type, map, updated) VALUES(:date, :type, :map, :updated)";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(date, type, map) DO UPDATE SET "
        + "updated=excluded.updated";

    private static RowMapper<Match> STD_ROW_MAPPER;
    private final ConversionService conversionService;

    @Autowired
    public MatchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "match", "30 DAYS");
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
        {
            Match match = new Match
            (
                rs.getLong("match.id"),
                rs.getObject("match.date", OffsetDateTime.class),
                conversionService.convert(rs.getInt("match.type"), BaseMatch.MatchType.class),
                rs.getString("match.map")
            );
            match.setUpdated(rs.getObject("match.updated", OffsetDateTime.class));
            return match;
        };
    }

    public static RowMapper<Match> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(Match match)
    {
        return new MapSqlParameterSource()
            .addValue("date", match.getDate())
            .addValue("type", conversionService.convert(match.getType(), Integer.class))
            .addValue("map", match.getMap())
            .addValue("updated", match.getUpdated());
    }

    public Match merge(Match match)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(match);
        getTemplate().update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        match.setId(keyHolder.getKey().longValue());
        return match;
    }

}
