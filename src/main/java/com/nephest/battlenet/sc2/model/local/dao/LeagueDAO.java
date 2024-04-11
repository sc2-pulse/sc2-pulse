// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueDAO
{
    public static final String STD_SELECT =
        "league.id AS \"league.id\","
        + "league.season_id AS \"league.season_id\","
        + "league.type AS \"league.type\","
        + "league.queue_type AS \"league.queue_type\","
        + "league.team_type AS \"league.team_type\" ";

    public static final String SHORT_SELECT =
        "league.type AS \"league.type\","
        + "league.queue_type AS \"league.queue_type\","
        + "league.team_type AS \"league.team_type\" ";

    private static final String CREATE_QUERY = "INSERT INTO league "
        + "(season_id, type, queue_type, team_type) "
        + "VALUES (:seasonId, :type, :queueType, :teamType)";

    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS "
        + "( "
            + "VALUES(:seasonId, :type, :queueType, :teamType) "
        + "), "
        + "selected AS "
        + "( "
            + "SELECT id "
            + "FROM league "
            + "INNER JOIN vals v(season_id, type, queue_type, team_type) USING(season_id, type, queue_type, team_type) "
        + "), "
        + "inserted AS "
        + "( "
            + "INSERT INTO league(season_id, type, queue_type, team_type)  "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "ON CONFLICT(season_id, type, queue_type, team_type) DO UPDATE SET "
            + "type=excluded.type "
            + "RETURNING id "
        + ") "
        + "SELECT id from selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String FIND_BY_IDS_QUERY =
        "SELECT " + STD_SELECT
        + "FROM league "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_SEASON_IDS_AND_UNIQUE_IDS =
        "SELECT " + STD_SELECT
        + "FROM league "
        + "WHERE season_id IN(:seasonIds) "
        + "AND (array_length(:types::smallint[], 1) IS NULL OR type = ANY(:types::smallint[])) "
        + "AND "
        + "("
            + "array_length(:queues::smallint[], 1) IS NULL "
            + "OR queue_type = ANY(:queues::smallint[])"
        + ") "
        + "AND (:teamType::smallint IS NULL OR team_type = :teamType::smallint)";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<League> STD_ROW_MAPPER;
    private static RowMapper<League> SHORT_ROW_MAPPER;

    @Autowired
    public LeagueDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new League
        (
            rs.getInt("league.id"),
            rs.getInt("league.season_id"),
            conversionService.convert(rs.getInt("league.type"), BaseLeague.LeagueType.class),
            conversionService.convert(rs.getInt("league.queue_type"), QueueType.class),
            conversionService.convert(rs.getInt("league.team_type"), TeamType.class)
        );
        if(SHORT_ROW_MAPPER == null) SHORT_ROW_MAPPER = (rs, num)-> new League
        (
            null,
            null,
            conversionService.convert(rs.getInt("league.type"), BaseLeague.LeagueType.class),
            conversionService.convert(rs.getInt("league.queue_type"), QueueType.class),
            conversionService.convert(rs.getInt("league.team_type"), TeamType.class)
        );
    }

    public static RowMapper<League> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static RowMapper<League> getShortRowMapper()
    {
        return SHORT_ROW_MAPPER;
    }

    public League create(League league)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(league);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        league.setId(keyHolder.getKey().intValue());
        return league;
    }

    @Cacheable(cacheNames = "fqdn-ladder-scan", keyGenerator = "fqdnSimpleKeyGenerator")
    public League merge(League league)
    {
        MapSqlParameterSource params = createParameterSource(league);
        league.setId(template.query(MERGE_QUERY, params, DAOUtils.INT_EXTRACTOR));
        return league;
    }

    public List<League> find(List<Integer> ids)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", ids);
        return template.query(FIND_BY_IDS_QUERY, params, STD_ROW_MAPPER);
    }

    public List<League> find
    (
        Set<Integer> seasonIds,
        Set<QueueType> queues,
        TeamType teamType,
        Set<BaseLeague.LeagueType> leagueTypes
    )
    {
        if(seasonIds.isEmpty())
        {
            if(!queues.isEmpty() || teamType != null || !leagueTypes.isEmpty())
                throw new IllegalArgumentException("Missing seasonIds");
            return List.of();
        }

        Integer[] queueIds = queues.stream()
            .map(queue->conversionService.convert(queue, Integer.class))
            .distinct()
            .toArray(Integer[]::new);
        Integer[] typeIds = leagueTypes.stream()
            .map(leagueType->conversionService.convert(leagueType, Integer.class))
            .distinct()
            .toArray(Integer[]::new);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonIds", seasonIds)
            .addValue("queues", queueIds.length == 0 ? null : queueIds)
            .addValue("types", typeIds.length == 0 ? null : typeIds)
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return template.query(FIND_BY_SEASON_IDS_AND_UNIQUE_IDS, params, STD_ROW_MAPPER);
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

