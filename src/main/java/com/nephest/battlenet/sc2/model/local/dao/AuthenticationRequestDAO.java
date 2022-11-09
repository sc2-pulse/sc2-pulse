// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.AuthenticationRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthenticationRequestDAO
extends StandardDAO
{

    public static final String STD_SELECT =
        "authentication_request.name AS \"authentication_request.name\", "
        + "authentication_request.created AS \"authentication_request.created\" ";
    private static final String MERGE =
        "INSERT INTO authentication_request(name, created) "
        + "VALUES(:name, :created) "
        + "ON CONFLICT(name) DO NOTHING";
    private static final String EXISTS =
        "SELECT EXISTS(SELECT 1 FROM authentication_request WHERE name = :name)";
    private static final String EXISTS_TTL =
        "SELECT EXISTS"
        + "("
            + "SELECT 1 "
            + "FROM authentication_request "
            + "WHERE name = :name "
            + "AND created >= :createdMin"
        + ")";
    private static final String FIND_BY_NAMES =
        "SELECT " + STD_SELECT
        + "FROM authentication_request "
        + "WHERE name IN(:names)";

    public static final RowMapper<AuthenticationRequest> STD_ROW_MAPPER = (rs, i)->new AuthenticationRequest
    (
        rs.getString("authentication_request.name"),
        rs.getObject("authentication_request.created", OffsetDateTime.class)
    );
    public static final ResultSetExtractor<AuthenticationRequest> STD_EXTRACTOR =
        DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);

    @Autowired
    public AuthenticationRequestDAO
    (
        NamedParameterJdbcTemplate template
    )
    {
        super(template, "authentication_request", "created", "60 SECONDS");
    }

    public AuthenticationRequest merge(AuthenticationRequest request)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", request.getName())
            .addValue("created", request.getCreated());
        getTemplate().update(MERGE, params);

        return request;
    }

    public boolean exists(String name)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name);
        return Boolean.TRUE.equals(getTemplate().queryForObject(EXISTS, params, Boolean.class));
    }

    public boolean exists(String name, Duration ttl)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name)
            .addValue("createdMin", OffsetDateTime.now().minus(ttl));
        return Boolean.TRUE.equals(getTemplate().queryForObject(EXISTS_TTL, params, Boolean.class));
    }

    public List<AuthenticationRequest> find(String... names)
    {
        if(names.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("names", Set.of(names));
        return getTemplate().query(FIND_BY_NAMES, params, STD_ROW_MAPPER);
    }

}
