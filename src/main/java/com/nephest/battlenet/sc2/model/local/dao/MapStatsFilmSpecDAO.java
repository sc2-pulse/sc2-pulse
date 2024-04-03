// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec.FRAME_DURATION_UNIT;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MapStatsFilmSpecDAO
{

    public static final int MATCH_UP_TEAM_SIZE = 1;

    public static final String STD_SELECT =
        "map_stats_film_spec.id AS \"map_stats_film_spec.id\", "
        + "map_stats_film_spec.race AS \"map_stats_film_spec.race\", "
        + "map_stats_film_spec.versus_race AS \"map_stats_film_spec.versus_race\", "
        + "map_stats_film_spec.frame_duration AS \"map_stats_film_spec.frame_duration\" ";

    private static final String CREATE =
        "INSERT INTO map_stats_film_spec(race, versus_race, frame_duration) "
        + "VALUES(:race, :versusRace, :frameDuration)";

    private static final String FIND_BY_SPEC =
        "SELECT " + STD_SELECT
        + "FROM map_stats_film_spec "
        + "WHERE (race, versus_race) IN (:matchUps) "
        + "AND frame_duration = :frameDuration";

    private static RowMapper<MapStatsFilmSpec> STD_MAPPER;
    private static ResultSetExtractor<MapStatsFilmSpec> STD_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public MapStatsFilmSpecDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initExtractors(conversionService);
    }

    private static void initExtractors(ConversionService conversionService)
    {
        if(STD_MAPPER == null) STD_MAPPER = (rs, i)->new MapStatsFilmSpec
        (
            rs.getInt("map_stats_film_spec.id"),
            conversionService.convert(rs.getInt("map_stats_film_spec.race"), Race.class),
            conversionService.convert(rs.getInt("map_stats_film_spec.versus_race"), Race.class),
            Duration.of(rs.getInt("map_stats_film_spec.frame_duration"), FRAME_DURATION_UNIT)
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_MAPPER);
    }

    public static RowMapper<MapStatsFilmSpec> getStdMapper()
    {
        return STD_MAPPER;
    }

    public static ResultSetExtractor<MapStatsFilmSpec> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    @CacheEvict(value = "map-stats-spec", allEntries = true)
    public MapStatsFilmSpec create(MapStatsFilmSpec spec)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("race", conversionService.convert(spec.getRace(), Integer.class))
            .addValue("versusRace", conversionService.convert(spec.getVersusRace(), Integer.class))
            .addValue("frameDuration", spec.getFrameDuration().get(FRAME_DURATION_UNIT));
        template.update(CREATE, params, keyHolder, new String[]{"id"});
        spec.setId(keyHolder.getKey().intValue());
        return spec;
    }

    @Cacheable("map-stats-spec")
    public List<MapStatsFilmSpec> find(Set<MatchUp> matchUps, Duration frameDuration)
    {
        if(matchUps.isEmpty()) return List.of();
        if(matchUps.stream().anyMatch(m->m.getTeamSize() != MATCH_UP_TEAM_SIZE))
            throw new IllegalArgumentException("Invalid team size");

        List<Object[]> matchUpIds = matchUps.stream()
            .map(matchUp->new Integer[] {
                    conversionService.convert(matchUp.getRaces().get(0), Integer.class),
                    conversionService.convert(matchUp.getVersusRaces().get(0), Integer.class),
                })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchUps", matchUpIds)
            .addValue("frameDuration", frameDuration.get(FRAME_DURATION_UNIT));
        return template.query(FIND_BY_SPEC, params, STD_MAPPER);
    }

}
