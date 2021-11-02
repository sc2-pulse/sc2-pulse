// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.SC2Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class SC2MapDAO
{

    public static final String STD_SELECT =
        "map.id AS \"map.id\", "
        + "map.name AS \"map.name\" ";

    private static final String MERGE =
        "WITH "
        + "vals AS (VALUES :maps), "
        + "existing AS "
        + "("
            + "SELECT " + STD_SELECT
            + "FROM map "
            + "INNER JOIN vals v(name) USING (name)"
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.name "
            + "FROM vals v(name) "
            + "LEFT JOIN existing ON v.name = existing.\"map.name\" "
            + "WHERE existing.\"map.id\" IS NULL"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO map(name) "
            + "SELECT * FROM missing "
            + "ON CONFLICT(name) DO UPDATE "
            + "SET name = excluded.name "
            + "RETURNING " + STD_SELECT
        + ") "
        + "SELECT * FROM existing "
        + "UNION "
        + "SELECT * FROM inserted";

    public static final RowMapper<SC2Map> STD_ROW_MAPPER = (rs, i)->new SC2Map
    (
        rs.getInt("map.id"),
        rs.getString("map.name")
    );

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public SC2MapDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public SC2Map[] merge(SC2Map... maps)
    {
        if(maps.length == 0) return maps;

        List<Object[]> data = Arrays.stream(maps)
            .distinct()
            .map(t->new Object[]{t.getName()})
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("maps", data);
        List<SC2Map> merged = template.query(MERGE, params, STD_ROW_MAPPER);

        return DAOUtils.updateOriginals(maps, merged, SC2Map.NATURAL_ID_COMPARATOR, (o, m)->o.setId(m.getId()));
    }

}
