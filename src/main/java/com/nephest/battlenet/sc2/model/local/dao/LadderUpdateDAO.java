// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderUpdateDAO
extends StandardDAO
{

    public static final Duration TTL = Duration.ofDays(30);

    public static final String STD_SELECT =
        "ladder_update.region AS \"ladder_update.region\", "
        + "ladder_update.queue_type AS \"ladder_update.queue_type\", "
        + "ladder_update.league_type AS \"ladder_update.league_type\", "
        + "ladder_update.created AS \"ladder_update.created\", "
        + "ladder_update.duration AS \"ladder_update.duration\" ";

    private static final String CREATE = "INSERT INTO "
        + "ladder_update(region, queue_type, league_type, created, duration) "
        + "VALUES(:region, :queueType, :leagueType, :created, :duration)";

    private static final String GET_ALL = "SELECT " + STD_SELECT + " FROM ladder_update";

    private static RowMapper<LadderUpdate> ROW_MAPPER;
    private static ResultSetExtractor<LadderUpdate> EXTRACTOR;

    private final ConversionService conversionService;

    @Autowired
    public LadderUpdateDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "ladder_update", "created", TTL.toHours() + " hours");
        this.conversionService = conversionService;
        if(ROW_MAPPER == null) ROW_MAPPER = (rs, i)->new LadderUpdate
        (
            conversionService.convert(rs.getInt("ladder_update.region"), Region.class),
            conversionService.convert(rs.getInt("ladder_update.queue_type"), QueueType.class),
            conversionService.convert
            (
                rs.getInt("ladder_update.league_type"),
                BaseLeague.LeagueType.class
            ),
            rs.getObject("ladder_update.created", OffsetDateTime.class),
            Duration.ofSeconds(rs.getInt("ladder_update.duration"))
        );
        if(EXTRACTOR == null) EXTRACTOR = DAOUtils.getResultSetExtractor(ROW_MAPPER);
    }

    public static RowMapper<LadderUpdate> getRowMapper()
    {
        return ROW_MAPPER;
    }

    public static ResultSetExtractor<LadderUpdate> getExtractor()
    {
        return EXTRACTOR;
    }

    public Set<LadderUpdate> create(Set<LadderUpdate> updates)
    {
        MapSqlParameterSource[] params = updates.stream()
            .map(u->new MapSqlParameterSource()
                .addValue("region", conversionService.convert(u.getRegion(), Integer.class))
                .addValue("queueType", conversionService.convert(u.getQueueType(), Integer.class))
                .addValue("leagueType", conversionService.convert(u.getLeagueType(), Integer.class))
                .addValue("created", u.getCreated())
                .addValue("duration", u.getDuration().toSeconds())
            )
            .toArray(MapSqlParameterSource[]::new);
        getTemplate().batchUpdate(CREATE, params);
        return updates;
    }

    public List<LadderUpdate> getAll()
    {
        return getTemplate().query(GET_ALL, ROW_MAPPER);
    }

}
