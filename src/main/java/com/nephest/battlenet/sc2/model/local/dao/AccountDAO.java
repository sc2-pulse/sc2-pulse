// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountDAO
{
    public static final String STD_SELECT =
        "account.id AS \"account.id\", "
        + "account.partition AS \"account.partition\", "
        + "account.battle_tag AS \"account.battle_tag\" ";

    private static final String CREATE_QUERY = "INSERT INTO account "
        + "(partition, battle_tag) "
        + "VALUES (:partition, :battleTag)";

    private static final String MERGE_QUERY =
        "WITH selected AS ("
            + "SELECT id FROM account WHERE partition = :partition AND battle_tag = :battleTag "
        + "), "
        + "inserted AS ("
            + "INSERT INTO account "
            + "(partition, battle_tag) "
            + "SELECT :partition, :battleTag "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "ON CONFLICT(partition, battle_tag) DO UPDATE SET "
            + "partition=excluded.partition "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String FIND_BY_PARTITION_AND_BATTLE_TAG =
        "SELECT " + STD_SELECT
        + "FROM account "
        + "WHERE partition = :partition "
        + "AND battle_tag = :battleTag";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT
        + "FROM account "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_ROLE =
        "SELECT " + STD_SELECT
        + "FROM account_role "
        + "INNER JOIN account ON account_role.account_id = account.id "
        + "WHERE account_role.role = :role "
        + "ORDER BY account.id";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<Account> STD_ROW_MAPPER;
    private static ResultSetExtractor<Account> STD_EXTRACTOR;

    @Autowired
    public AccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
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

        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<Account> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Account> getStdExtractor()
    {
        return STD_EXTRACTOR;
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
        MapSqlParameterSource params = createParameterSource(account);
        account.setId(template.query(MERGE_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return account;
    }

    public Optional<Account> find(Partition partition, String battleTag)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("partition", conversionService.convert(partition, Integer.class))
            .addValue("battleTag", battleTag);
        return Optional.ofNullable(template.query(FIND_BY_PARTITION_AND_BATTLE_TAG, params, STD_EXTRACTOR));
    }

    public List<Account> findByIds(Long... ids)
    {
        if(ids.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", List.of(ids));
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

    public List<Account> findByRole(SC2PulseAuthority authority)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("role", conversionService.convert(authority, Integer.class));
        return template.query(FIND_BY_ROLE, params, STD_ROW_MAPPER);
    }

    private MapSqlParameterSource createParameterSource(Account account)
    {
        return new MapSqlParameterSource()
            .addValue("partition", conversionService.convert(account.getPartition(), Integer.class))
            .addValue("battleTag", account.getBattleTag());
    }

}

