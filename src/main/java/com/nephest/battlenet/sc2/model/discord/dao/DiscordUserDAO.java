// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.dao;

import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DiscordUserDAO
{

    public static final String STD_SELECT =
        "discord_user.id AS \"discord_user.id\", "
        + "discord_user.name AS \"discord_user.name\", "
        + "discord_user.discriminator AS \"discord_user.discriminator\" ";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT
        + "FROM discord_user "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_ACCOUNT_ID =
        "SELECT " + STD_SELECT
        + "FROM account_discord_user "
        + "INNER JOIN discord_user ON account_discord_user.discord_user_id = discord_user.id "
        + "WHERE account_discord_user.account_id = :accountId "
        + "AND(NOT :respectVisibility OR account_discord_user.public)";

    private static final String FIND_BY_ID_CURSOR_TEMPLATE =
        "FROM discord_user "
        + "WHERE id > :idCursor "
        + "ORDER BY id "
        + "LIMIT :limit";

    private static final String FIND_BY_ID_CURSOR =
        "SELECT " + STD_SELECT
        + FIND_BY_ID_CURSOR_TEMPLATE;

    private static final String FIND_IDS_BY_ID_CURSOR =
        "SELECT id "
        + FIND_BY_ID_CURSOR_TEMPLATE;

    private static final String MERGE =
        "WITH "
        + "vals AS (VALUES :users), "
        + "updated AS "
        + "( "
            + "UPDATE discord_user "
            + "SET name = v.name, "
            + "discriminator = v.discriminator "
            + "FROM vals v(id, name, discriminator) "
            + "WHERE discord_user.id = v.id "
            + "AND "
            + "( "
                + "discord_user.name != v.name "
                + "OR discord_user.discriminator != v.discriminator "
            + ") "
            + "RETURNING 1 "
        + "), "
        + "inserted AS "
        + "( "
            + "INSERT INTO discord_user(id, name, discriminator) "
            + "SELECT * FROM "
            + "( "
                + "SELECT v.id, v.name, v.discriminator "
                + "FROM vals v(id, name, discriminator) "
                + "LEFT JOIN discord_user USING(id) "
                + "WHERE discord_user.id IS NULL "
            + ") missing "
            + "ON CONFLICT(id) DO UPDATE "
            + "SET name = excluded.name, "
            + "discriminator = excluded.discriminator "
            + "RETURNING 1 "
        + ") "
        + "SELECT COUNT(*) FROM updated, inserted";

    private static final String DELETE_USERS_WITH_NO_ACCOUNT =
        "DELETE FROM discord_user "
        + "USING discord_user du "
        + "LEFT JOIN account_discord_user ON du.id = account_discord_user.discord_user_id "
        + "WHERE discord_user.id = du.id "
        + "AND account_discord_user.discord_user_id IS NULL";

    public static final RowMapper<DiscordUser> STD_ROW_MAPPER = (rs, i)->new DiscordUser
    (
        rs.getLong("discord_user.id"),
        rs.getString("discord_user.name"),
        rs.getInt("discord_user.discriminator")
    );

    public static final ResultSetExtractor<DiscordUser> STD_EXTRACTOR =
        DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public DiscordUserDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public List<DiscordUser> find(Long... ids)
    {
        if(ids.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", List.of(ids));
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

    public Optional<DiscordUser> findByAccountId(Long accountId, boolean respectVisibility)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("respectVisibility", respectVisibility);
        return Optional.ofNullable(template.query(FIND_BY_ACCOUNT_ID, params, STD_EXTRACTOR));
    }

    public List<DiscordUser> findByIdCursor(Long idCursor, int size)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idCursor", idCursor)
            .addValue("limit", size);
        return template.query(FIND_BY_ID_CURSOR, params, STD_ROW_MAPPER);
    }

    public List<Long> findIdsByIdCursor(Long idCursor, int size)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idCursor", idCursor)
            .addValue("limit", size);
        return template.query(FIND_IDS_BY_ID_CURSOR, params, DAOUtils.LONG_MAPPER);
    }

    public DiscordUser[] merge(DiscordUser... users)
    {
        if(users.length == 0) return new DiscordUser[0];

        List<Object[]> data = Arrays.stream(users)
            .filter(Objects::nonNull)
            .distinct()
            .map(u->new Object[]{
                u.getId(),
                u.getName(),
                u.getDiscriminator()
            }).collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("users", data);
        template.query(MERGE, params, DAOUtils.INT_MAPPER);

        return users;
    }

    public int removeUsersWithNoAccountLinked()
    {
        return template.update(DELETE_USERS_WITH_NO_ACCOUNT, new MapSqlParameterSource());
    }

}
