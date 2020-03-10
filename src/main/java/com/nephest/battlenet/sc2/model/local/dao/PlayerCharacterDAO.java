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
public class PlayerCharacterDAO
{

    private static final String MERGE_QUERY = "INSERT INTO player_character "
        + "(account_id, battlenet_id, realm, name) "
        + "VALUES (:accountId, :battlenetId, :realm, :name) "

        + "ON DUPLICATE KEY UPDATE "
        + "id=LAST_INSERT_ID(id),"
        + "account_id=VALUES(account_id), "
        + "battlenet_id=VALUES(battlenet_id), "
        + "realm=VALUES(realm), "
        + "name=VALUES(name)";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public PlayerCharacterDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public PlayerCharacter merge(PlayerCharacter character)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", character.getAccountId())
            .addValue("battlenetId", character.getBattlenetId())
            .addValue("realm", character.getRealm())
            .addValue("name", character.getName());
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        character.setId( (Long) keyHolder.getKeyList().get(0).get("insert_id"));
        return character;
    }

}
