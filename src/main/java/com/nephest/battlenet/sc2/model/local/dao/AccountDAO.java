// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Collections;

@Repository
public class AccountDAO
{
    private static final String CREATE_QUERY = "INSERT INTO account "
        + "(battle_tag, updated) "
        + "VALUES (:battleTag, :updated)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(battle_tag) DO UPDATE SET "
        + "updated=excluded.updated";

    private static final String REMOVE_EXPIRED_PRIVACY_QUERY =
        "DELETE FROM account WHERE updated < NOW() - INTERVAL '30 DAYS'";

    private final NamedParameterJdbcTemplate template;
    private final PostgreSQLUtils postgreSQLUtils;

    @Autowired
    public AccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        PostgreSQLUtils postgreSQLUtils
    )
    {
        this.template = template;
        this.postgreSQLUtils = postgreSQLUtils;
    }

    public Account create(Account account)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(account);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        account.setId(keyHolder.getKey().longValue());
        return account;
    }

    public Account merge(Account account)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(account);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        account.setId(keyHolder.getKey().longValue());
        return account;
    }

    private MapSqlParameterSource createParameterSource(Account account)
    {
        return new MapSqlParameterSource()
            .addValue("battleTag", account.getBattleTag())
            .addValue("updated", account.getUpdated());
    }

    public void removeExpiredByPrivacy()
    {
        template.update(REMOVE_EXPIRED_PRIVACY_QUERY, Collections.emptyMap());
        postgreSQLUtils.vacuum();
    }

}

