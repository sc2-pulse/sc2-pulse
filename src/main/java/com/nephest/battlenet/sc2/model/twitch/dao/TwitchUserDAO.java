// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch.dao;

import com.nephest.battlenet.sc2.model.twitch.TwitchUser;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TwitchUserDAO
{

    public static final String STD_SELECT =
        "twitch_user.id AS \"twitch_user.id\", "
        + "twitch_user.login AS \"twitch_user.login\" ";

    private static final String MERGE = "WITH "
        + "vals AS (VALUES :users), "
        + "existing AS "
        + "("
            + "SELECT id "
            + "FROM vals v(id, login) "
            + "INNER JOIN twitch_user USING(id) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE twitch_user "
            + "SET login = v.login "
            + "FROM vals v(id, login) "
            + "WHERE twitch_user.id = v.id "
            + "AND twitch_user.login != v.login "
            + "RETURNING v.id "
        + "), "
        + "missing AS "
        + "("
            + "SELECT * "
            + "FROM vals v(id, login) "
            + "LEFT JOIN existing USING(id) "
            + "WHERE existing.id IS null"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO twitch_user(id, login) "
            + "SELECT * FROM missing "
            + "ON CONFLICT DO NOTHING "
            + "RETURNING twitch_user.id "
        + ") "
        + "SELECT * FROM updated "
        + "UNION "
        + "SELECT * FROM inserted";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT + "FROM twitch_user WHERE id IN(:ids)";

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<TwitchUser> STD_ROW_MAPPER = (rs, i)->new TwitchUser
    (
        rs.getLong("twitch_user.id"),
        rs.getString("twitch_user.login")
    );

    @Autowired
    public TwitchUserDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public TwitchUser[] merge(TwitchUser... users)
    {
        if(users.length == 0) return users;

        List<Object[]> data = Arrays.stream(users)
            .distinct()
            .map(u->new Object[]{u.getId(), u.getLogin()})
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("users", data);
        template.queryForList(MERGE, params, Long.class);
        return users;
    }

    public List<TwitchUser> findById(Long... ids)
    {
        if(ids.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", List.of(ids));
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

}
