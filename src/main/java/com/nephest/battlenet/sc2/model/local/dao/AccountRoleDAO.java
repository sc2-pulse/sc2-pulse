// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AccountRoleDAO
{

    private static final String GET_ROLES_QUERY =
        "SELECT role AS \"account_role\" FROM account_role WHERE account_id = :accountId";
    private static final String ADD_ROLES_QUERY =
        "INSERT INTO account_role(account_id, role) VALUES(:accountId, :role)";
    private static final String REMOVE_ROLES_QUERY =
        "DELETE FROM account_role WHERE account_id = :accountId AND role IN(:roles)";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<SC2PulseAuthority> STD_ROW_MAPPER;

    @Autowired
    public AccountRoleDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
            conversionService.convert(rs.getInt("account_role"), SC2PulseAuthority.class);
    }

    public List<SC2PulseAuthority> getRoles(long accountId)
    {
        List<SC2PulseAuthority> roles =
            template.query(GET_ROLES_QUERY, new MapSqlParameterSource().addValue("accountId", accountId), STD_ROW_MAPPER);
        if(roles.contains(SC2PulseAuthority.NONE)) return List.of(SC2PulseAuthority.NONE);

        roles.add(SC2PulseAuthority.USER);
        return roles;
    }

    public int[] addRoles(long accountId, SC2PulseAuthority... roles)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[roles.length];
        for(int i = 0; i < roles.length; i++) params[i] = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("role", conversionService.convert(roles[i], Integer.class));
        return template.batchUpdate(ADD_ROLES_QUERY, params);
    }

    public int removeRoles(long accountId, SC2PulseAuthority... roles)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("roles", Arrays.stream(roles)
                .map(r->conversionService.convert(r, Integer.class))
                .collect(Collectors.toSet()));
        return template.update(REMOVE_ROLES_QUERY, params);
    }

}
