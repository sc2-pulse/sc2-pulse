// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgreSQLUtils
{

    public static final String TRANSACTION_USER_ID_PARAMETER_NAME = "sc2pulse.user_id";

    private static final String GET_APPROXIMATE_COUNT_QUERY =
        "SELECT reltuples::bigint FROM pg_class WHERE relname = ?";
    private static final String SET_TRANSACTION_USER_ID_QUERY =
        "SELECT set_config('" + TRANSACTION_USER_ID_PARAMETER_NAME + "', ?, true)";
    private static final String GET_TRANSACTION_USER_ID_QUERY =
        "SELECT current_setting('" + TRANSACTION_USER_ID_PARAMETER_NAME + "', true)";

    private final JdbcTemplate template;

    public PostgreSQLUtils(@Autowired JdbcTemplate template)
    {
        this.template = template;
    }

    public static String escapeLikePattern(String pattern)
    {
        return pattern.replaceAll("\\\\", "\\\\\\\\")
            .replaceAll("%", "\\\\%")
            .replaceAll("_", "\\\\_");
    }

    public void analyze()
    {
        template.execute("ANALYZE");
    }

    public void vacuum()
    {
        template.execute("VACUUM");
    }

    public void vacuumAnalyze()
    {
        template.execute("VACUUM(ANALYZE)");
    }

    //use carefully, sql injection is possible
    public void reindex(Set<String> indexes, boolean concurrently)
    {
        String head = concurrently ? "REINDEX INDEX CONCURRENTLY " : "REINDEX INDEX ";
        for(String ix : indexes) template.execute(head + ix);
    }

    public Long getApproximateCount(String table)
    {
        return template.query(GET_APPROXIMATE_COUNT_QUERY, DAOUtils.LONG_EXTRACTOR, table);
    }

    public String setTransactionUserId(String id)
    {
        String result = template.queryForObject(SET_TRANSACTION_USER_ID_QUERY, String.class, id);
        return result == null || result.isEmpty() ? null : result;
    }

    public String getTransactionUserId()
    {
        String id = template.queryForObject(GET_TRANSACTION_USER_ID_QUERY, String.class);
        return id == null || id.isEmpty() ? null : id;
    }

}
