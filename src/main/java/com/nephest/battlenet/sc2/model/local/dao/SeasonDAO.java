/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
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
        + "id, battlenet_id, region, year, number "
        + "FROM season "
        + "WHERE region=:region "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_MAX_BATTLENET_ID_QUERY =
        "SELECT MAX(battlenet_id) FROM season";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    private final RowMapper<Season> FIND_LIST_BY_REGION_ROW_MAPPER =
    (rs, num)->
    {
        return new Season
        (
            rs.getLong("id"),
            rs.getLong("battlenet_id"),
            conversionService.convert(rs.getInt("region"), Region.class),
            rs.getInt("year"),
            rs.getInt("number")
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
        return template.query(FIND_LIST_BY_REGION, params, FIND_LIST_BY_REGION_ROW_MAPPER);
    }

    @Cacheable(cacheNames="search-season-last")
    public long getMaxBattlenetId()
    {
        return template.query(FIND_MAX_BATTLENET_ID_QUERY, DAOUtils.LONG_EXTRACTOR);
    }

}
