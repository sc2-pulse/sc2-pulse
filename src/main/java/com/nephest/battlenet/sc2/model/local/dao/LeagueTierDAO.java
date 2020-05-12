// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.LeagueTier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueTierDAO
{
    private static final String CREATE_QUERY = "INSERT INTO league_tier "
        + "(league_id, type, min_rating, max_rating) "
        + "VALUES (:leagueId, :type, :minRating, :maxRating)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(league_id, type) DO UPDATE SET "
        + "min_rating=excluded.min_rating, "
        + "max_rating=excluded.max_rating";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    @Autowired
    public LeagueTierDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public LeagueTier create(LeagueTier tier)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(tier);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        tier.setId(keyHolder.getKey().longValue());
        return tier;
    }

    public LeagueTier merge(LeagueTier tier)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(tier);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        tier.setId(keyHolder.getKey().longValue());
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

}
