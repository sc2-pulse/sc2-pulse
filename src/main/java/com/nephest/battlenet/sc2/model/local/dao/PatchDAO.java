// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.Patch;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PatchDAO
{

    public static final String STD_SELECT =
        "patch.build AS \"patch.build\", "
        + "patch.version AS \"patch.version\", "
        + "patch.versus AS \"patch.versus\" ";

    private static final String MERGE =
        "WITH updated AS "
        + "("
            + "UPDATE patch "
            + "SET version = :version, "
            + "versus = :versus "
            + "WHERE build = :build "
            + "RETURNING build "
        + ") "
        + "INSERT INTO patch(build, version, versus) "
        + "SELECT :build, :version, :versus "
        + "WHERE NOT EXISTS (SELECT 1 FROM updated) ";

    private static final String FIND_BY_BUILD_MIN =
        "SELECT " + STD_SELECT
        + "FROM patch "
        + "WHERE build >= :buildMin "
        + "ORDER BY build DESC";

    public static final RowMapper<Patch> STD_ROW_MAPPER = (rs, i)->new Patch
    (
        rs.getLong("patch.build"),
        rs.getString("patch.version"),
        DAOUtils.getBoolean(rs, "patch.versus")
    );

    public static final ResultSetExtractor<Patch> STD_EXTRACTOR =
        DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public PatchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    @Transactional
    public int[] merge(Set<Patch> patches)
    {
        if(patches.isEmpty()) return DAOUtils.EMPTY_INT_ARRAY;

        SqlParameterSource[] params = patches.stream()
            .map(BeanPropertySqlParameterSource::new)
            .toArray(SqlParameterSource[]::new);
        return template.batchUpdate(MERGE, params);
    }

    public List<Patch> findByBuildMin(Long buildMin)
    {
        MapSqlParameterSource params = new MapSqlParameterSource("buildMin", buildMin);
        return template.query(FIND_BY_BUILD_MIN, params, STD_ROW_MAPPER);
    }

}
