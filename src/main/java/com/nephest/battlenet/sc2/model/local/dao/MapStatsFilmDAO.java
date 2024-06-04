// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.MapStatsFilm;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MapStatsFilmDAO
{

    public static final String STD_SELECT =
        "map_stats_film.id AS \"map_stats_film.id\", "
        + "map_stats_film.map_id AS \"map_stats_film.map_id\", "
        + "map_stats_film.league_tier_id AS \"map_stats_film.league_tier_id\", "
        + "map_stats_film.map_stats_film_spec_id AS \"map_stats_film.map_stats_film_spec_id\", "
        + "map_stats_film.cross_tier AS \"map_stats_film.cross_tier\" ";

    private static final String FIND_BY_UNIQUE_IDS =
        "SELECT " + STD_SELECT
        + "FROM map_stats_film "
        + "WHERE map_stats_film_spec_id IN(:mapStatsFilmSpecIds) "
        + "AND league_tier_id IN(:leagueTierIds) "
        + "AND (array_length(:mapIds::integer[], 1) IS NULL OR map_id = ANY(:mapIds::integer[])) "
        + "AND "
        + "("
            + "array_length(:crossTier::boolean[], 1) IS NULL "
            + "OR cross_tier = ANY(:crossTier::boolean[]) "
        + ")";

    public static final RowMapper<MapStatsFilm> STD_MAPPER = (rs, i)->new MapStatsFilm
    (
        rs.getInt("map_stats_film.id"),
        rs.getInt("map_stats_film.map_id"),
        rs.getInt("map_stats_film.league_tier_id"),
        rs.getInt("map_stats_film.map_stats_film_spec_id"),
        rs.getBoolean("map_stats_film.cross_tier")
    );

    public static final ResultSetExtractor<MapStatsFilm> STD_EXTRACTOR
        = DAOUtils.getResultSetExtractor(STD_MAPPER);

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public MapStatsFilmDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public List<MapStatsFilm> find
    (
        Set<Integer> specIds,
        Set<Integer> tierIds,
        Set<Integer> mapIds,
        Set<Boolean> crossTier
    )
    {
        if(specIds.isEmpty() || tierIds.isEmpty())
            throw new IllegalArgumentException("Missing specIds or tierIds");

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("mapStatsFilmSpecIds", specIds)
            .addValue("leagueTierIds", tierIds)
            .addValue("mapIds", mapIds.isEmpty() ? null : mapIds)
            .addValue("crossTier", crossTier.isEmpty() ? null : crossTier.toArray(Boolean[]::new));
        return template.query(FIND_BY_UNIQUE_IDS, params, STD_MAPPER);
    }

}
