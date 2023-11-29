// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.Patch;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PatchDAO
{

    public static final String STD_SELECT =
        "patch.id AS \"patch.id\", "
        + "patch.build AS \"patch.build\", "
        + "patch.version AS \"patch.version\", "
        + "patch.versus AS \"patch.versus\" ";

    private static final String MERGE =
        "WITH vals AS(VALUES :patches), "
        + "existing AS "
        + "( "
            + "SELECT " + STD_SELECT
            + "FROM vals v(build, version, versus) "
            + "INNER JOIN patch USING(build, version) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE patch "
            + "SET versus = v.versus::boolean "
            + "FROM vals v(build, version, versus) "
            + "WHERE patch.build = v.build "
            + "AND patch.version = v.version "
            + "AND patch.versus IS DISTINCT FROM v.versus::boolean "
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.build, v.version, v.versus::boolean "
            + "FROM vals v(build, version, versus) "
            + "LEFT JOIN existing ON v.build = existing.\"patch.build\" "
                + "AND v.version = existing.\"patch.version\" "
            + "WHERE existing.\"patch.build\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO patch(build, version, versus) "
            + "SELECT * FROM missing "
            + "RETURNING " + STD_SELECT
        + ") "
        + "SELECT * FROM existing "
        + "UNION "
        + "SELECT * FROM inserted ";

    private static final String FIND_BY_BUILD_MIN =
        "SELECT " + STD_SELECT
        + "FROM patch "
        + "WHERE build >= :buildMin "
        + "ORDER BY build DESC";

    public static final RowMapper<Patch> STD_ROW_MAPPER = (rs, i)->new Patch
    (
        rs.getInt("patch.id"),
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

    public Set<Patch> merge(Set<Patch> patches)
    {
        if(patches.isEmpty()) return Set.of();

        List<Object[]> data = patches.stream()
            .sorted(Comparator.nullsLast(Comparator.comparing(Patch::getVersus)))
            .map(patch->new Object[]{
                patch.getBuild(),
                patch.getVersion(),
                patch.getVersus()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource("patches", data);
        List<Patch> merged = template.query(MERGE, params, STD_ROW_MAPPER);
        return DAOUtils.updateOriginals(patches, merged, (o, u)->o.setId(u.getId()));
    }

    public List<Patch> findByBuildMin(Long buildMin)
    {
        MapSqlParameterSource params = new MapSqlParameterSource("buildMin", buildMin);
        return template.query(FIND_BY_BUILD_MIN, params, STD_ROW_MAPPER);
    }

}
