// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProTeamMemberDAO
{

    public static final String STD_SELECT
        = "pro_team_member.pro_team_id AS \"pro_team_member.pro_team_id\", "
        + "pro_team_member.pro_player_id AS \"pro_team_member.pro_player_id\", "
        + "pro_team_member.updated AS \"pro_team_member.updated\" ";

    private static final String CREATE_QUERY =
        "INSERT INTO pro_team_member (pro_team_id, pro_player_id, updated) "
        + "VALUES (:proTeamId, :proPlayerId, :updated)";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(pro_player_id) DO UPDATE SET "
        + "pro_team_id=excluded.pro_team_id, "
        + "updated=excluded.updated";

    private static final String REMOVE_BY_PRO_PLAYER_IDS =
        "DELETE FROM pro_team_member WHERE pro_player_id IN(:proPlayerIds)";

    private static final String FIND_BY_PRO_PLAYER_IDS =
        "SELECT " + STD_SELECT
        + "FROM pro_team_member "
        + "WHERE pro_player_id IN(:pro_player_ids)";

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<ProTeamMember> STD_ROW_MAPPER = (rs, i)->
    {
        ProTeamMember ptm = new ProTeamMember
        (
            rs.getLong("pro_team_member.pro_team_id"),
            rs.getLong("pro_team_member.pro_player_id")
        );
        ptm.setUpdated(rs.getObject("pro_team_member.updated", OffsetDateTime.class));
        return ptm;
    };

    public static final ResultSetExtractor<ProTeamMember> STD_EXTRACTOR
        = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);

    @Autowired
    public ProTeamMemberDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
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
        return template.update(REMOVE_BY_PRO_PLAYER_IDS, params);
    }

    public List<ProTeamMember> findByProPlayerIds(Long... proPlayerIds)
    {
        if(proPlayerIds.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("pro_player_ids", Set.of(proPlayerIds));
        return template.query(FIND_BY_PRO_PLAYER_IDS, params, STD_ROW_MAPPER);
    }

}
