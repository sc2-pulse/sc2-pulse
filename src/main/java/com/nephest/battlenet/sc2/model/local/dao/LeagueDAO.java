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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.nephest.battlenet.sc2.model.local.League;

@Repository
public class LeagueDAO
{

    private static final String MERGE_QUERY = "INSERT INTO league "
        + "(season_id, type, queue_type, team_type) "
        + "VALUES (:seasonId, :type, :queueType, :teamType) "

        + "ON DUPLICATE KEY UPDATE "
        + "id=LAST_INSERT_ID(id), "
        + "season_id=VALUES(season_id), "
        + "type=VALUES(type), "
        + "queue_type=VALUES(queue_type), "
        + "team_type=VALUES(team_type)";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    @Autowired
    public LeagueDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public League merge(League league)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", league.getSeasonId())
            .addValue("type", conversionService.convert(league.getType(), Integer.class))
            .addValue("queueType", conversionService.convert(league.getQueueType(), Integer.class))
            .addValue("teamType", conversionService.convert(league.getTeamType(), Integer.class));
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        league.setId( (Long) keyHolder.getKeyList().get(0).get("insert_id"));
        return league;
    }

}

