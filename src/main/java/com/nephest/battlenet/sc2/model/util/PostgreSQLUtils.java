// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Repository
public class PostgreSQLUtils
{

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

}
