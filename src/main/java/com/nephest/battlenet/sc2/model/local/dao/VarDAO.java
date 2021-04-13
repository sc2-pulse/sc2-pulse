// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class VarDAO
{

    private static final String MERGE_QUERY =
        "INSERT INTO \"var\" (\"key\", \"value\") "
        + "VALUES (:key, :value) "
        + "ON CONFLICT(\"key\") DO UPDATE SET "
        + "value=excluded.value";

    private static final String FIND_QUERY = "SELECT \"value\" FROM \"var\" WHERE \"key\"=:key";

    private final NamedParameterJdbcTemplate template;

    public VarDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    public int merge(String key, String val)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("key", key)
            .addValue("value", val);
        return template.update(MERGE_QUERY, params);
    }

    public Optional<String> find(String key)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("key", key);
        return Optional.ofNullable(template.query(FIND_QUERY, params, DAOUtils.STRING_EXTRACTOR));
    }

}
