// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ClanDAO
{

    public static final String STD_SELECT =
        "clan.id AS \"clan.id\", "
        + "clan.tag AS \"clan.tag\", "
        + "clan.region AS \"clan.region\", "
        + "clan.name AS \"clan.name\" ";

    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :clans), "
        + "existing AS "
        + "("
            + "SELECT " + STD_SELECT
            + "FROM vals v(tag, region, name) "
            + "INNER JOIN clan USING(tag, region)"
        + "), "
        + "updated AS "
        + "("
            + "UPDATE clan "
            + "SET name = COALESCE(v.name, clan.name) "
            + "FROM vals v (tag, region, name) "
            + "WHERE clan.tag = v.tag "
            + "AND clan.region = v.region "
            + "AND v.name IS NOT NULL "
            + "AND clan.name != v.name "
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.tag, v.region, v.name "
            + "FROM vals v (tag, region, name) "
            + "LEFT JOIN existing ON v.tag = existing.\"clan.tag\" AND v.region = existing.\"clan.region\" "
            + "WHERE existing.\"clan.id\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO clan (tag, region, name) "
            + "SELECT * FROM missing "
            + "ON CONFLICT(tag, region) DO UPDATE "
            + "SET name = COALESCE(excluded.name, clan.name) "
            + "RETURNING "
            + "id AS \"clan.id\", "
            + "tag AS \"clan.tag\", "
            + "region AS \"clan.region\", "
            + "name AS \"clan.name\" "
        + ") "
        + "SELECT * FROM existing "
        + "UNION "
        + "SELECT * FROM inserted";

    private static final String FIND_IDS_BY_ID_CURSOR =
        "SELECT id "
        + "FROM clan "
        + "WHERE id > :cursor "
        + "ORDER BY id ASC "
        + "LIMIT :limit";

    private static RowMapper<Clan> STD_ROW_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public ClanDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
            new Clan
            (
                rs.getInt("clan.id"),
                rs.getString("clan.tag"),
                conversionService.convert(rs.getInt("clan.region"), Region.class),
                rs.getString("clan.name")
            );
    }

    public static RowMapper<Clan> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(Clan clan)
    {
        return new MapSqlParameterSource()
            .addValue("tag", clan.getTag())
            .addValue("name", clan.getName());
    }

    public Clan[] merge(Clan... clans)
    {
        if(clans.length == 0) return new Clan[0];

        List<Object[]> clanData = Arrays.stream(clans)
            .distinct()
            .map(clan->new Object[]{
                clan.getTag(),
                conversionService.convert(clan.getRegion(), Integer.class),
                clan.getName()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("clans", clanData);

        List<Clan> mergedClans = template.query(MERGE_QUERY, params, STD_ROW_MAPPER);

        return DAOUtils.updateOriginals(clans, mergedClans, (o, m)->o.setId(m.getId()));
    }

    public List<Integer> findIds(Integer cursor, int count)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("cursor", cursor)
            .addValue("limit", count);
        return template.query(FIND_IDS_BY_ID_CURSOR, params, DAOUtils.INT_MAPPER);
    }

}
