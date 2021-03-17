// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;

public class StandardDAO
{

    public static final String DEFAULT_EXPIRATION_COLUMN = "updated";

    private final String REMOVE_EXPIRED_QUERY;
    private final NamedParameterJdbcTemplate template;


    public StandardDAO(NamedParameterJdbcTemplate template, String table, String column, String period)
    {
        this.template = template;
        REMOVE_EXPIRED_QUERY = String.format(DAOUtils.REMOVE_OUTDATED_TEMPLATE, table, column, period);
    }

    public StandardDAO(NamedParameterJdbcTemplate template, String table, String period)
    {
        this(template, table, DEFAULT_EXPIRATION_COLUMN, period);
    }

    public int removeExpired()
    {
        return template.update(REMOVE_EXPIRED_QUERY, Collections.emptyMap());
    }

    public NamedParameterJdbcTemplate getTemplate()
    {
        return template;
    }

}
