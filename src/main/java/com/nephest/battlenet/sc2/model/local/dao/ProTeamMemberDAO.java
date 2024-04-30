// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
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

    public int[] merge(Set<ProTeamMember> proTeamMembers)
    {
        if(proTeamMembers.isEmpty()) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = proTeamMembers.stream()
            .peek(proTeamMember->proTeamMember.setUpdated(SC2Pulse.offsetDateTime()))
            .map(this::createParameterSource)
            .toArray(MapSqlParameterSource[]::new);

        return template.batchUpdate(MERGE_QUERY, params);
    }

    public int remove(Set<Long> proPlayerIds)
    {
        if(proPlayerIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerIds", proPlayerIds);
        return template.update(REMOVE_BY_PRO_PLAYER_IDS, params);
    }

    public List<ProTeamMember> findByProPlayerIds(Set<Long> proPlayerIds)
    {
        if(proPlayerIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("pro_player_ids", proPlayerIds);
        return template.query(FIND_BY_PRO_PLAYER_IDS, params, STD_ROW_MAPPER);
    }

}
