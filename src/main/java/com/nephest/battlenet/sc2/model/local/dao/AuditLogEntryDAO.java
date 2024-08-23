// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.AuditLogEntry;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogEntryDAO
{

    public static final String STD_SELECT = """
        audit.logged_actions.event_id AS "audit_log.id",
        audit.logged_actions.action_tstamp_clk AS "audit_log.created",
        audit.logged_actions.schema_name AS "audit_log.schema",
        audit.logged_actions.table_name AS "audit_log.table",
        audit.logged_actions.action AS "audit_log.action",
        audit.logged_actions.row_data::text AS "audit_log.data",
        audit.logged_actions.changed_fields::text AS "audit_log.changed_data",
        audit.logged_actions.session_user_id::bigint AS "audit_log.author_account_id"
        """;

    private static final String SEARCH_TEMPLATE = """
        SELECT %s
        FROM audit.logged_actions
        WHERE (action_tstamp_clk, event_id) < (:createdCursor, :idCursor)
        AND schema_name = :schema
        AND table_name IN(:tables)
        AND (:action::text IS NULL OR action = :action::text)
        AND (:authorAccountId::bigint IS NULL OR session_user_id = :authorAccountId::bigint)
        AND (:excludeSystemAuthor::boolean = false OR session_user_id IS NOT NULL)
        %s
        ORDER BY action_tstamp_clk DESC, event_id DESC
        LIMIT :limit
        """;

    private static final String FIND_REVEALER_LOG = SEARCH_TEMPLATE.formatted
    (
        STD_SELECT,
        """
        AND
        (
            :accountId::bigint IS NULL
            OR (row_data ->> 'account_id')::bigint = :accountId::bigint
        )
        """
    );


    private static RowMapper<AuditLogEntry> STD_ROW_MAPPER;
    private static ResultSetExtractor<AuditLogEntry> STD_EXTRACTOR;

    public static final String REVEALER_SCHEMA = "public";
    public static final Set<String> REVEALER_TABLES = Set.of
    (
        "pro_player",
        "pro_player_account",
        "social_media_link",
        "pro_team",
        "pro_team_member",
        "twitch_user"
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public AuditLogEntryDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("auditLogConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new AuditLogEntry
        (
            rs.getLong("audit_log.id"),
            rs.getObject("audit_log.created", OffsetDateTime.class),
            rs.getString("audit_log.schema"),
            rs.getString("audit_log.table"),
            conversionService.convert(rs.getString("audit_log.action"), AuditLogEntry.Action.class),
            rs.getString("audit_log.data"),
            rs.getString("audit_log.changed_data"),
            DAOUtils.getLong(rs, "audit_log.author_account_id")
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<AuditLogEntry> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<AuditLogEntry> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    private List<AuditLogEntry> find
    (
        @NotNull String schema,
        @NotNull Set<String> tables,
        @NotNull Consumer<MapSqlParameterSource> paramAppender,
        @NotNull String query,
        int limit,
        boolean excludeSystemAuthor,
        @Nullable Long authorAccountId,
        @Nullable AuditLogEntry.Action action,
        @Nullable OffsetDateTime createdCursor,
        @Nullable Long idCursor
    )
    {
        if(limit == 0 || tables.isEmpty()) return List.of();
        if(limit < 0) throw new IllegalArgumentException("Expected limit >= 0");

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("createdCursor", createdCursor)
            .addValue("idCursor", idCursor)
            .addValue("schema", schema)
            .addValue("tables", tables.isEmpty() ? null : tables)
            .addValue("limit", limit)
            .addValue("excludeSystemAuthor", excludeSystemAuthor)
            .addValue("authorAccountId", authorAccountId)
            .addValue("action", conversionService.convert(action, String.class));
        paramAppender.accept(params);
        return template.query(query, params, STD_ROW_MAPPER);
    }

    public List<AuditLogEntry> findRevealerLog
    (
        int limit,
        boolean excludeSystemAuthor,
        @Nullable Long authorAccountId,
        @Nullable AuditLogEntry.Action action,
        @Nullable OffsetDateTime createdCursor,
        @Nullable Long idCursor,
        @Nullable Long accountId
    )
    {
        return find
        (
            REVEALER_SCHEMA,
            REVEALER_TABLES,
            params->params.addValue("accountId", accountId),
            FIND_REVEALER_LOG,
            limit,
            excludeSystemAuthor,
            authorAccountId,
            action,
            createdCursor,
            idCursor
        );
    }


}
