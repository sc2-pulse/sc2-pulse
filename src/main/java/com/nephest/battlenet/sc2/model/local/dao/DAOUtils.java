// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

public final class DAOUtils
{

    public static final String REMOVE_OUTDATED_TEMPLATE = "DELETE FROM %1$s WHERE updated < NOW() - INTERVAL '%2$s'";

    private DAOUtils(){}

    public static final ResultSetExtractor<Long> LONG_EXTRACTOR =
    (rs)->
    {
        rs.next();
        long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

    public static final ResultSetExtractor<Integer> INT_EXTRACTOR =
    (rs)->
    {
        rs.next();
        int val = rs.getInt(1);
        return rs.wasNull() ? null : val;
    };

    public static final RowMapper<Long> LONG_MAPPER =
    (rs, ix)->
    {
        Long val = rs.getLong(1);
        return rs.wasNull() ? null : val;
    };

}
