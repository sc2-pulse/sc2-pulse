// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collection;
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

    private static final String FIND_BY_IDS_QUERY =
        "SELECT " + STD_SELECT
        + "FROM map "
        + "WHERE id IN (:ids)";

    private static final String FIND_BY_LADDER_QUERY =
        "WITH map_filter AS "
        + "("
            + "SELECT DISTINCT(map_id) "
            + "FROM map_stats "
            + "INNER JOIN league ON map_stats.league_id = league.id "
            + "INNER JOIN season ON league.season_id = season.id "
            + "WHERE season.battlenet_id = :seasonId "
            + "AND season.region IN (:regions) "
            + "AND league.queue_type = :queueType "
            + "AND league.team_type = :teamType "
            + "AND league.type IN (:leagueTypes)"
        + ") "
        + "SELECT " + STD_SELECT
        + "FROM map_filter "
        + "INNER JOIN map ON map_filter.map_id = map.id "
        + "ORDER BY id";

    public static final RowMapper<SC2Map> STD_ROW_MAPPER = (rs, i)->new SC2Map
    (
        rs.getInt("map.id"),
        rs.getString("map.name")
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public SC2MapDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
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

        return DAOUtils.updateOriginals(maps, merged, (o, m)->o.setId(m.getId()));
    }

    public List<SC2Map> find(List<Integer> ids)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", ids);
        return template.query(FIND_BY_IDS_QUERY, params, STD_ROW_MAPPER);
    }

    public List<SC2Map> find
    (
        int season,
        Collection<Region> regions,
        Collection<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params = LadderUtil
            .createSearchParams(conversionService, season, regions, leagueTypes, queueType, teamType);
        return template.query(FIND_BY_LADDER_QUERY, params, STD_ROW_MAPPER);
    }


}
