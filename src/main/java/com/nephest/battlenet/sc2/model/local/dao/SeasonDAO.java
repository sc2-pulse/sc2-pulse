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

import java.time.*;
import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.core.convert.*;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;

@Repository
public class SeasonDAO
{

    private static final String MERGE_QUERY = "INSERT INTO season "
        + "(battlenet_id, region, year, number) "
        + "VALUES (:battlenetId, :region, :year, :number) "

        + "ON DUPLICATE KEY UPDATE "
        + "id=LAST_INSERT_ID(id),"
        + "battlenet_id=VALUES(battlenet_id), "
        + "region=VALUES(region), "
        + "year=VALUES(year), "
        + "number=VALUES(number) ";

    private static final String FIND_LIST_BY_REGION = "SELECT "
        + "id, battlenet_id, region, year, number "
        + "FROM season "
        + "WHERE region=:region "
        + "ORDER BY battlenet_id DESC";

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

    public Season merge(Season season)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("battlenetId", season.getBattlenetId())
            .addValue("region", conversionService.convert(season.getRegion(), Integer.class))
            .addValue("year", season.getYear())
            .addValue("number", season.getNumber());
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        season.setId( (Long) keyHolder.getKeyList().get(0).get("insert_id"));
        return season;
    }

    public List<Season> findListByRegion(Region region)
    {
        MapSqlParameterSource params
            = new MapSqlParameterSource("region", conversionService.convert(region, Integer.class));
        return template.query(FIND_LIST_BY_REGION, params, FIND_LIST_BY_REGION_ROW_MAPPER);
    }

}
