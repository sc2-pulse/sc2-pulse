// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProTeam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class ProTeamDAO
extends StandardDAO
{

    private static RowMapper<ProTeam> STD_ROW_MAPPER;

    public static final String STD_SELECT =
        "pro_team.id AS \"pro_team.id\", "
        + "pro_team.aligulac_id AS \"pro_team.aligulac_id\", "
        + "pro_team.name AS \"pro_team.name\", "
        + "pro_team.short_name AS \"pro_team.short_name\", "
        + "pro_team.updated AS \"pro_team.updated\"";
    private static final String CREATE_QUERY =
        "INSERT INTO pro_team (aligulac_id, name, short_name, updated) "
        + "VALUES (:aligulacId, :name, :shortName, :updated)";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(LOWER(REPLACE(name, ' ', ''))) DO UPDATE SET "
        + "short_name=excluded.short_name, "
        + "aligulac_id=excluded.aligulac_id, "
        + "updated=excluded.updated";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public  ProTeamDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        super(template, "pro_team", "30 DAYS");
        this.template = template;
        initMappers();
    }

    private static void initMappers()
    {
        if (STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) ->
        {
            ProTeam proTeam =  new ProTeam
            (
                rs.getLong("pro_team.id"),
                rs.getLong("pro_team.aligulac_id"),
                rs.getString("pro_team.name"),
                rs.getString("pro_team.short_name")
            );
            proTeam.setUpdated(rs.getObject("pro_team.updated", OffsetDateTime.class));
            return proTeam;
        };
    }

    public static RowMapper<ProTeam> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(ProTeam proTeam)
    {
        return new MapSqlParameterSource()
            .addValue("aligulacId", proTeam.getAligulacId())
            .addValue("name", proTeam.getName())
            .addValue("shortName", proTeam.getShortName())
            .addValue("updated", proTeam.getUpdated());
    }

    public ProTeam merge(ProTeam proTeam)
    {
        proTeam.setUpdated(OffsetDateTime.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(proTeam);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        proTeam.setId(keyHolder.getKey().longValue());
        return proTeam;
    }

    public int[] merge(ProTeam... proTeams)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[proTeams.length];
        for(int i = 0; i < proTeams.length; i++)
        {
            proTeams[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proTeams[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

}
