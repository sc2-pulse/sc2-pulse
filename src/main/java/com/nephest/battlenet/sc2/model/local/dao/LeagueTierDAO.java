// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LeagueTierDAO
{

    public static final String STD_SELECT =
        "league_tier.id AS \"league_tier.id\","
        + "league_tier.league_id AS \"league_tier.league_id\","
        + "league_tier.type AS \"league_tier.type\","
        + "league_tier.min_rating AS \"league_tier.min_rating\","
        + "league_tier.max_rating AS \"league_tier.max_rating\" ";

    private static final String CREATE_QUERY = "INSERT INTO league_tier "
        + "(league_id, type, min_rating, max_rating) "
        + "VALUES (:leagueId, :type, :minRating, :maxRating)";

    private static final String MERGE_QUERY =
        "WITH vals AS (VALUES(:leagueId, :type, :minRating, :maxRating)), "
        + "selected AS "
        + "("
            + "SELECT id, league_id, type "
            + "FROM league_tier "
            + "INNER JOIN vals v(league_id, type, min_rating, max_rating) USING (league_id, type) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE league_tier "
            + "SET min_rating = v.min_rating, "
            + "max_rating = v.max_rating "
            + "FROM selected "
            + "INNER JOIN vals v(league_id, type, min_rating, max_rating) USING (league_id, type) "
            + "WHERE league_tier.id = selected.id "
            + "AND (league_tier.min_rating != v.min_rating OR league_tier.max_rating != v.max_rating) "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO league_tier(league_id, type, min_rating, max_rating) "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "ON CONFLICT(league_id, type) DO UPDATE SET "
            + "min_rating=excluded.min_rating, "
            + "max_rating=excluded.max_rating "
            + "RETURNING id "
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String FIND_BY_LADDER_QUERY =
        "SELECT " + STD_SELECT + " FROM league_tier "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "WHERE season.battlenet_id = :season "
        + "AND season.region = :region "
        + "AND league.type = :leagueType "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType "
        + "AND league_tier.type = :tierType";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<LeagueTier> STD_ROW_MAPPER;
    private static ResultSetExtractor<LeagueTier> STD_EXTRACTOR;

    @Autowired
    public LeagueTierDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new LeagueTier
        (
            rs.getInt("league_tier.id"),
            rs.getInt("league_tier.league_id"),
            conversionService.convert(rs.getInt("league_tier.type"), BaseLeagueTier.LeagueTierType.class),
            rs.getInt("league_tier.min_rating"),
            rs.getInt("league_tier.max_rating")
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = (rs)->
        {
            if(!rs.next()) return null;
            return getStdRowMapper().mapRow(rs, 0);
        };
    }

    public static RowMapper<LeagueTier> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<LeagueTier> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public LeagueTier create(LeagueTier tier)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(tier);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        tier.setId(keyHolder.getKey().intValue());
        return tier;
    }

    @Cacheable(cacheNames = "ladder-skeleton")
    public LeagueTier merge(LeagueTier tier)
    {
        MapSqlParameterSource params = createParameterSource(tier);
        tier.setId(template.query(MERGE_QUERY, params, DAOUtils.INT_EXTRACTOR));
        return tier;
    }

    private MapSqlParameterSource createParameterSource(LeagueTier tier)
    {
        return new MapSqlParameterSource()
            .addValue("leagueId", tier.getLeagueId())
            .addValue("type", conversionService.convert(tier.getType(), Integer.class))
            .addValue("minRating", tier.getMinRating())
            .addValue("maxRating", tier.getMaxRating());
    }

    public Optional<LeagueTier> findByLadder
    (int season, Region region, BaseLeague.LeagueType leagueType, QueueType queueType, TeamType teamType, BaseLeagueTier.LeagueTierType tierType)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("leagueType", conversionService.convert(leagueType, Integer.class))
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class))
            .addValue("tierType", conversionService.convert(tierType, Integer.class));
        return Optional.ofNullable(template.query(FIND_BY_LADDER_QUERY, params, getStdExtractor()));
    }

}
