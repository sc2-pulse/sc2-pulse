// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SeasonDAO
{
    private static final String CREATE_QUERY = "INSERT INTO season "
        + "(battlenet_id, region, year, number) "
        + "VALUES (:battlenetId, :region, :year, :number)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(region, battlenet_id) DO UPDATE SET "
        + "year=excluded.year, "
        + "number=excluded.number";

    private static final String FIND_LIST_BY_REGION = "SELECT "
        + "id AS \"season.id\", battlenet_id AS \"season.battlenet_id\", region AS \"season.region\", "
        + "year AS \"season.year\", number AS \"season.number\""
        + "FROM season "
        + "WHERE region=:region "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_MAX_BATTLENET_ID_QUERY =
        "SELECT MAX(battlenet_id) FROM season";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    private final RowMapper<Season> STD_ROW_MAPPER =
    (rs, num)->
    {
        return new Season
        (
            rs.getLong("season.id"),
            rs.getLong("season.battlenet_id"),
            conversionService.convert(rs.getInt("season.region"), Region.class),
            rs.getInt("season.year"),
            rs.getInt("season.number")
        );
    };


    @Autowired
    public SeasonDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public Season create(Season season)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(season);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        season.setId(keyHolder.getKey().longValue());
        return season;
    }

    public Season merge(Season season)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(season);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        season.setId(keyHolder.getKey().longValue());
        return season;
    }

    private MapSqlParameterSource createParameterSource(Season season)
    {
        return new MapSqlParameterSource()
            .addValue("battlenetId", season.getBattlenetId())
            .addValue("region", conversionService.convert(season.getRegion(), Integer.class))
            .addValue("year", season.getYear())
            .addValue("number", season.getNumber());
    }

    public List<Season> findListByRegion(Region region)
    {
        MapSqlParameterSource params
            = new MapSqlParameterSource("region", conversionService.convert(region, Integer.class));
        return template.query(FIND_LIST_BY_REGION, params, STD_ROW_MAPPER);
    }

    @Cacheable(cacheNames="search-season-last")
    public Long getMaxBattlenetId()
    {
        return template.query(FIND_MAX_BATTLENET_ID_QUERY, DAOUtils.LONG_EXTRACTOR);
    }

    public RowMapper<Season> getStandardRowMapper()
    {
        return STD_ROW_MAPPER;
    }

}
