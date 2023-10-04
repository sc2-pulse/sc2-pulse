// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.AccountProperty;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPropertyDAO
{

    public static final String STD_SELECT =
        "account_property.account_id AS \"account_property.account_id\", "
        + "account_property.type AS \"account_property.type\", "
        + "account_property.value AS \"account_property.value\" ";

    private static final String MERGE =
        "WITH vals AS (VALUES :data), "
        + "updated AS "
        + "("
            + "UPDATE account_property "
            + "SET value = v.value "
            + "FROM vals v(account_id, type, value) "
            + "WHERE account_property.account_id = v.account_id "
            + "AND account_property.type = v.type "
            + "RETURNING v.account_id, v.type"
        + ") "
        + "INSERT INTO account_property(account_id, type, value) "
        + "SELECT v.* "
        + "FROM vals v(account_id, type, value) "
        + "LEFT JOIN account_property ON account_property.account_id = v.account_id "
            + "AND account_property.type = v.type "
        + "WHERE account_property.account_id IS NULL "
        + "ON CONFLICT(account_id, type) DO UPDATE "
        + "SET value = excluded.value";


    private static final String FIND_BY_ACCOUNT_ID_AND_TYPE =
        "SELECT " + STD_SELECT
        + "FROM account_property "
        + "WHERE account_id = :accountId "
        + "AND type = :type";

    private static RowMapper<AccountProperty> STD_ROW_MAPPER;
    private static ResultSetExtractor<AccountProperty> STD_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public AccountPropertyDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new AccountProperty
        (
            rs.getLong("account_property.account_id"),
            conversionService.convert(rs.getInt("account_property.type"), AccountProperty.PropertyType.class),
            rs.getString("account_property.value")
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<AccountProperty> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<AccountProperty> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public int merge(Set<AccountProperty> properties)
    {
        if(properties.isEmpty()) return 0;

        List<Object[]> data = properties.stream()
            .map(p->new Object[]{
                p.getAccountId(),
                conversionService.convert(p.getType(), Integer.class),
                p.getValue()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("data", data);
        return template.update(MERGE, params);
    }

    public Optional<AccountProperty> find(Long accountId, AccountProperty.PropertyType type)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("type", conversionService.convert(type, Integer.class));
        return Optional.ofNullable(template.query(FIND_BY_ACCOUNT_ID_AND_TYPE, params, STD_EXTRACTOR));
    }


}
