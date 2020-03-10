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

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.core.convert.*;

import com.nephest.battlenet.sc2.model.local.*;

@Repository
public class DivisionDAO
{

    private static final String MERGE_QUERY = "INSERT INTO division "
        + "(league_tier_id, battlenet_id) "
        + "VALUES (:leagueTierId, :battlenetId) "

        + "ON DUPLICATE KEY UPDATE "
        + "id=LAST_INSERT_ID(id),"
        + "league_tier_id=VALUES(league_tier_id), "
        + "battlenet_id=VALUES(battlenet_id)";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public DivisionDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public Division merge(Division division)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("leagueTierId", division.getTierId())
            .addValue("battlenetId", division.getBattlenetId());
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        division.setId( (Long) keyHolder.getKeyList().get(0).get("insert_id"));
        return division;
    }

}
