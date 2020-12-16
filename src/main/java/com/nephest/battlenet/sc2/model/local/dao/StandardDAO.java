// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;

public class StandardDAO
{

    private final String REMOVE_EXPIRED_QUERY;
    private final NamedParameterJdbcTemplate template;


    public StandardDAO(NamedParameterJdbcTemplate template, String table, String period)
    {
        this.template = template;
        REMOVE_EXPIRED_QUERY = String.format(DAOUtils.REMOVE_OUTDATED_TEMPLATE, table, period);
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
