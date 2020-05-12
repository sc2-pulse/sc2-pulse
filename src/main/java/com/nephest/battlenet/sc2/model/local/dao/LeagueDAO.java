// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.League;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueDAO
{
    private static final String CREATE_QUERY = "INSERT INTO league "
        + "(season_id, type, queue_type, team_type) "
        + "VALUES (:seasonId, :type, :queueType, :teamType)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(season_id, type, queue_type, team_type) DO UPDATE SET "
        + "type=excluded.type";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    @Autowired
    public LeagueDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public League create(League league)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(league);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        league.setId(keyHolder.getKey().longValue());
        return league;
    }

    public League merge(League league)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(league);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        league.setId(keyHolder.getKey().longValue());
        return league;
    }

    private MapSqlParameterSource createParameterSource(League league)
    {
        return new MapSqlParameterSource()
            .addValue("seasonId", league.getSeasonId())
            .addValue("type", conversionService.convert(league.getType(), Integer.class))
            .addValue("queueType", conversionService.convert(league.getQueueType(), Integer.class))
            .addValue("teamType", conversionService.convert(league.getTeamType(), Integer.class));
    }

}

