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

import java.time.LocalDate;
import java.util.List;

@Repository
public class SeasonDAO
{
    public static final String STD_SELECT =
        "season.id AS \"season.id\","
        + "season.battlenet_id AS \"season.battlenet_id\","
        + "season.region AS \"season.region\","
        + "season.year AS \"season.year\","
        + "season.number AS \"season.number\","
        + "season.\"start\" AS \"season.start\", "
        + "season.\"end\" AS \"season.end\" ";

    private static final String CREATE_QUERY = "INSERT INTO season "
        + "(battlenet_id, region, year, number, \"start\", \"end\") "
        + "VALUES (:battlenetId, :region, :year, :number, :start, :end)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(region, battlenet_id) DO UPDATE SET "
        + "year=excluded.year, "
        + "number=excluded.number, "
        + "\"start\"=excluded.start, "
        + "\"end\"=excluded.end";

    private static final String FIND_LIST_BY_REGION = "SELECT "
        + STD_SELECT
        + "FROM season "
        + "WHERE region=:region "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_LIST_BY_FIRST_BATTELENET_ID =
        "SELECT DISTINCT ON (battlenet_id) "
        + STD_SELECT
        + "FROM season "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_MAX_BATTLENET_ID_QUERY =
        "SELECT MAX(battlenet_id) FROM season";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<Season> STD_ROW_MAPPER;


    @Autowired
    public SeasonDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER =
        (rs, num)-> new Season
        (
            rs.getLong("season.id"),
            rs.getInt("season.battlenet_id"),
            conversionService.convert(rs.getInt("season.region"), Region.class),
            rs.getInt("season.year"),
            rs.getInt("season.number"),
            rs.getObject("season.start", LocalDate.class),
            rs.getObject("season.end", LocalDate.class)
        );
    }

    public static RowMapper<Season> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
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
            .addValue("number", season.getNumber())
            .addValue("start", season.getStart())
            .addValue("end", season.getEnd());
    }

    public List<Season> findListByRegion(Region region)
    {
        MapSqlParameterSource params
            = new MapSqlParameterSource("region", conversionService.convert(region, Integer.class));
        return template.query(FIND_LIST_BY_REGION, params, STD_ROW_MAPPER);
    }

    public List<Season> findListByFirstBattlenetId()
    {
        return template.query(FIND_LIST_BY_FIRST_BATTELENET_ID, STD_ROW_MAPPER);
    }

    @Cacheable(cacheNames="search-season-last")
    public Integer getMaxBattlenetId()
    {
        return template.query(FIND_MAX_BATTLENET_ID_QUERY, DAOUtils.INT_EXTRACTOR);
    }

    public RowMapper<Season> getStandardRowMapper()
    {
        return STD_ROW_MAPPER;
    }

}
