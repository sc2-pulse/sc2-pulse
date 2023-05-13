// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProTeamMemberDAO
extends StandardDAO
{

    private static final String CREATE_QUERY =
        "INSERT INTO pro_team_member (pro_team_id, pro_player_id, updated) "
        + "VALUES (:proTeamId, :proPlayerId, :updated)";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(pro_player_id) DO UPDATE SET "
        + "pro_team_id=excluded.pro_team_id, "
        + "updated=excluded.updated";

    private static final String REMOVE_BY_PRO_PLAYER_IDS =
        "DELETE FROM pro_team_member WHERE pro_player_id IN(:proPlayerIds)";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public ProTeamMemberDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        super(template, "pro_team_member", "7 DAYS");
        this.template = template;
    }

    private MapSqlParameterSource createParameterSource(ProTeamMember proTeamMember)
    {
        return new MapSqlParameterSource()
            .addValue("proTeamId", proTeamMember.getProTeamId())
            .addValue("proPlayerId", proTeamMember.getProPlayerId())
            .addValue("updated", proTeamMember.getUpdated());
    }

    public int[] merge(ProTeamMember... proTeamMembers)
    {
        if(proTeamMembers.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[proTeamMembers.length];
        for(int i = 0; i < proTeamMembers.length; i++)
        {
            proTeamMembers[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proTeamMembers[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

    public int remove(Long... proPlayerIds)
    {
        if(proPlayerIds.length == 0) return 0;

        Set<Long> uniqueIds = Arrays.stream(proPlayerIds)
            .collect(Collectors.toSet());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerIds", uniqueIds);
        return getTemplate().update(REMOVE_BY_PRO_PLAYER_IDS, params);
    }

}
