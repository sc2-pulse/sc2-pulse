// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Collections;

@Repository
public class AccountDAO
{
    public static final String STD_SELECT =
        "account.id AS \"account.id\", "
        + "account.partition AS \"account.partition\", "
        + "account.battle_tag AS \"account.battle_tag\", "
        + "account.updated AS \"account.updated\" ";

    private static final String CREATE_QUERY = "INSERT INTO account "
        + "(partition, battle_tag, updated) "
        + "VALUES (:partition, :battleTag, :updated)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(partition, battle_tag) DO UPDATE SET "
        + "updated=excluded.updated";

    private static final String REMOVE_EXPIRED_PRIVACY_QUERY =
        "DELETE FROM account WHERE updated < NOW() - INTERVAL '30 DAYS'";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final PostgreSQLUtils postgreSQLUtils;

    private static RowMapper<Account> STD_ROW_MAPPER;

    @Autowired
    public AccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        PostgreSQLUtils postgreSQLUtils
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.postgreSQLUtils = postgreSQLUtils;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new Account
        (
            rs.getLong("account.id"),
            conversionService.convert(rs.getInt("account.partition"), Partition.class),
            rs.getString("account.battle_tag")
        );
    }

    public static RowMapper<Account> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
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
            .addValue("partition", conversionService.convert(account.getPartition(), Integer.class))
            .addValue("battleTag", account.getBattleTag())
            .addValue("updated", account.getUpdated());
    }

    public void removeExpiredByPrivacy()
    {
        template.update(REMOVE_EXPIRED_PRIVACY_QUERY, Collections.emptyMap());
        postgreSQLUtils.vacuum();
    }

}

