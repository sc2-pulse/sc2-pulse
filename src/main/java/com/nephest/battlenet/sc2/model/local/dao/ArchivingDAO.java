// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class ArchivingDAO<T>
{

    private static final Logger LOG = LoggerFactory.getLogger(ArchivingDAO.class);

    private final String archiveByRangeQuery;
    private final String archiveByIdQuery;
    private final NamedParameterJdbcTemplate template;
    private final String tableName;
    private final InstantVar from;
    private final Duration expirationDuration;

    public ArchivingDAO
    (
        NamedParameterJdbcTemplate template,
        VarDAO varDAO,
        String tableName,
        String archiveByRangeQuery,
        String archiveByIdQuery,
        Duration expirationDuration
    )
    {
        this.template = template;
        this.tableName = tableName;
        this.expirationDuration = expirationDuration;
        this.from = new InstantVar(varDAO, tableName + ".archiving.from", false);
        this.from.tryLoad(Instant.EPOCH);
        this.archiveByRangeQuery = archiveByRangeQuery;
        this.archiveByIdQuery = archiveByIdQuery;
    }

    public int archive(@Nullable OffsetDateTime fromOdt)
    {
        OffsetDateTime to = SC2Pulse.offsetDateTime().minus(expirationDuration);
        if(fromOdt == null) fromOdt = from.getValue().atOffset(ZoneOffset.UTC);
        if(!fromOdt.isBefore(to))
        {
            LOG.warn("'From' {} is not before 'to' {}. Invalid var value?", fromOdt, to);
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", fromOdt)
            .addValue("to", to);
        int count = template.update(archiveByRangeQuery, params);
        if(count > 0) LOG.info("Archived {} rows in {} table", count, tableName);
        from.setValueAndSave(to.toInstant());
        return count;
    }

    public int archive(T id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id);
        return template.update(archiveByIdQuery, params);
    }

}
