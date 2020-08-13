// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Division;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DivisionDAO
{

    private static final String CREATE_QUERY = "INSERT INTO division "
        + "(league_tier_id, battlenet_id) "
        + "VALUES (:leagueTierId, :battlenetId)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(league_tier_id, battlenet_id) DO UPDATE SET "
        + "battlenet_id=excluded.battlenet_id";

    private static final String FIND_LIST_BY_LADDER =
        "SELECT division.id, division.league_tier_id, division.battlenet_id "
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id=league_tier.id "
        + "INNER JOIN league ON league_tier.league_id=league.id "
        + "INNER JOIN season ON league.season_id=season.id "
        + "WHERE season.battlenet_id=:seasonBattlenetId AND season.region=:region "
        + "AND league.type=:leagueType AND league.queue_type=:queueType AND league.team_type=:teamType "
        + "AND league_tier.type=:tierType";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    public final RowMapper<Division> STD_ROW_MAPPER = (rs, num)-> new Division
    (
        rs.getLong("id"),
        rs.getLong("league_tier_id"),
        rs.getLong("battlenet_id")
    );

    @Autowired
    public DivisionDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public Division create(Division division)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createPamarameterSource(division);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        division.setId(keyHolder.getKey().longValue());
        return division;
    }

    public Division merge(Division division)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createPamarameterSource(division);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        division.setId(keyHolder.getKey().longValue());
        return division;
    }

    private MapSqlParameterSource createPamarameterSource(Division division)
    {
        return new MapSqlParameterSource()
            .addValue("leagueTierId", division.getTierId())
            .addValue("battlenetId", division.getBattlenetId());
    }

    public List<Division> findListByLadder
    (
        Integer season, Region region,
        BaseLeague.LeagueType leagueType, QueueType queueType, TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType
    )
    {
        return template.query
        (
            FIND_LIST_BY_LADDER,
            new MapSqlParameterSource()
                .addValue("seasonBattlenetId", season)
                .addValue("region", conversionService.convert(region, Integer.class))
                .addValue("leagueType", conversionService.convert(leagueType, Integer.class))
                .addValue("queueType", conversionService.convert(queueType, Integer.class))
                .addValue("teamType", conversionService.convert(teamType, Integer.class))
                .addValue("tierType", conversionService.convert(tierType, Integer.class)),
            STD_ROW_MAPPER
        );
    }

}
