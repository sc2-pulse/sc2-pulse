// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerCharacterReportDAO
{

    public static final String STD_SELECT =
        "player_character_report.id AS \"player_character_report.id\", "
        + "player_character_report.player_character_id AS \"player_character_report.player_character_id\", "
        + "player_character_report.additional_player_character_id AS \"player_character_report.additional_player_character_id\", "
        + "player_character_report.type AS \"player_character_report.type\", "
        + "player_character_report.status AS \"player_character_report.status\", "
        + "player_character_report.restrictions AS \"player_character_report.restrictions\", "
        + "player_character_report.status_change_timestamp AS \"player_character_report.status_change_timestamp\" ";

    private static final String MERGE_QUERY =
        "WITH existing AS ("
            + "SELECT id "
            + "FROM player_character_report "
            + "WHERE player_character_id = :playerCharacterId "
            + "AND type = :type "
            + "AND COALESCE(additional_player_character_id, -1) = COALESCE(:additionalPlayerCharacterId, -1) "
        + "), "
        + "inserted AS ("
            + "INSERT INTO "
            + "player_character_report(player_character_id, additional_player_character_id, type, status, status_change_timestamp) "
            + "SELECT :playerCharacterId, :additionalPlayerCharacterId, :type, :status, :statusChangeTimestamp "
            + "WHERE NOT EXISTS(SELECT 1 FROM existing) "
            + "ON CONFLICT(player_character_id, type, COALESCE(additional_player_character_id, -1)) DO UPDATE SET "
            + "type = excluded.type, "
            + "status = excluded.status, "
            + "status_change_timestamp = excluded.status_change_timestamp "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM existing "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String GET_ALL_QUERY =
        "SELECT " + STD_SELECT + " FROM player_character_report";

    private static final String FIND_BY_ID_CURSOR =
        "SELECT " + STD_SELECT
        + "FROM player_character_report "
        + "WHERE id > :idCursor "
        + "ORDER BY id ASC "
        + "LIMIT :limit";

    private static final String UPDATE_STATUS_TAIL =
        "report_status_agg AS ("
            + "SELECT player_character_report.id, "
            + "player_character_report.status AS report_status, "
            + "array_agg(evidence.status) AS evidence_status "
            + "FROM report_filter "
            + "INNER JOIN player_character_report USING(id) "
            + "INNER JOIN evidence ON player_character_report.id = evidence.player_character_report_id "
            + "GROUP BY player_character_report.id, player_character_report.status "
        + "), "
        + "report_status AS ("
            + "SELECT id, "
            + "CASE "
                + "WHEN true = ANY(evidence_status) THEN true "
                + "WHEN true = ANY(SELECT unnest(evidence_status) IS NULL) THEN null "
                + "ELSE false "
            + "END AS status "
            + "FROM report_status_agg "
        + ") "
        + "UPDATE player_character_report "
        + "SET status = report_status.status, "
        + "status_change_timestamp = NOW() "
        + "FROM report_status "
        + "WHERE player_character_report.id = report_status.id "
        + "AND player_character_report.status IS DISTINCT FROM report_status.status";

    private static final String UPDATE_STATUS_QUERY =
        "WITH report_filter AS ("
            + "SELECT DISTINCT(player_character_report.id) "
            + "FROM player_character_report "
            + "INNER JOIN evidence ON player_character_report.id = evidence.player_character_report_id "
            + "WHERE evidence.status_change_timestamp >= :from "
        + "), "
        + UPDATE_STATUS_TAIL;

    private static final String UPDATE_STATUS_BY_IDS_QUERY =
        "WITH report_filter AS (SELECT * FROM unnest(:ids) AS id), "
        + UPDATE_STATUS_TAIL;

    private static final String REMOVE_EMPTY_QUERY =
        """
        DELETE FROM player_character_report
        USING player_character_report r
        LEFT JOIN evidence ON evidence.player_character_report_id = r.id
        WHERE r.id IN(:ids)
        AND evidence.id IS NULL
        AND player_character_report.id = r.id 
        """;

    private static RowMapper<PlayerCharacterReport> STD_ROW_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public PlayerCharacterReportDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new PlayerCharacterReport
        (
            rs.getInt("player_character_report.id"),
            rs.getLong("player_character_report.player_character_id"),
            DAOUtils.getLong(rs, "player_character_report.additional_player_character_id"),
            conversionService.convert(rs.getInt("player_character_report.type"),
                PlayerCharacterReport.PlayerCharacterReportType.class),
            DAOUtils.getBoolean(rs, "player_character_report.status"),
            rs.getBoolean("player_character_report.restrictions"),
            rs.getObject("player_character_report.status_change_timestamp", OffsetDateTime.class)
        );
    }

    public static RowMapper<PlayerCharacterReport> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public PlayerCharacterReport merge(PlayerCharacterReport report)
    {
        if(Objects.equals(report.getPlayerCharacterId(), report.getAdditionalPlayerCharacterId()))
            throw new IllegalArgumentException("Main and additional characters ids are equal");
        if(report.getType() == PlayerCharacterReport.PlayerCharacterReportType.LINK
            && report.getAdditionalPlayerCharacterId() == null)
            throw new IllegalArgumentException("Link report must provide an additional character id");
        report.setId(template.query(MERGE_QUERY, createParameterSource(report), DAOUtils.INT_EXTRACTOR));
        return report;
    }

    public List<PlayerCharacterReport> getAll()
    {
        return template.query(GET_ALL_QUERY, STD_ROW_MAPPER);
    }

    public List<PlayerCharacterReport> findByIdCursor(int idCursor, int limit)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idCursor", idCursor)
            .addValue("limit", limit);
        return template.query(FIND_BY_ID_CURSOR, params, STD_ROW_MAPPER);
    }

    public int updateStatus(OffsetDateTime from)
    {
        return template.update(UPDATE_STATUS_QUERY, new MapSqlParameterSource().addValue("from", from));
    }

    public int updateStatus(Set<Integer> ids)
    {
        if(ids.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids.toArray(Integer[]::new), Types.ARRAY);
        return template.update(UPDATE_STATUS_BY_IDS_QUERY, params);
    }

    public int removeEmpty(Set<Integer> ids)
    {
        if(ids.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return template.update(REMOVE_EMPTY_QUERY, params);
    }

    private MapSqlParameterSource createParameterSource(PlayerCharacterReport report)
    {
        return new MapSqlParameterSource()
            .addValue("playerCharacterId", report.getPlayerCharacterId())
            .addValue("additionalPlayerCharacterId", report.getAdditionalPlayerCharacterId())
            .addValue("type", conversionService.convert(report.getType(), Integer.class))
            .addValue("status", report.getStatus())
            .addValue("restrictions", report.getRestrictions())
            .addValue("statusChangeTimestamp", report.getStatusChangeDateTime());
    }

}
