// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ClanMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ClanMemberDAO
extends StandardDAO
{

    public static final String STD_SELECT =
        "clan_member.clan_id AS \"clan_member.clan_id\", "
        + "clan_member.player_character_id AS \"clan_member.player_character_id\", "
        + "clan_member.updated AS \"clan_member.updated\" ";

    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :clanMembers), "
        + "updated AS "
        + "("
            + "UPDATE clan_member "
            + "SET clan_id = v.clan_id, "
            + "updated = NOW() "
            + "FROM vals v (clan_id, player_character_id) "
            + "WHERE clan_member.player_character_id = v.player_character_id "
            + "RETURNING " + STD_SELECT
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.clan_id, v.player_character_id, NOW() "
            + "FROM vals v (clan_id, player_character_id) "
            + "LEFT JOIN updated ON v.player_character_id = updated.\"clan_member.player_character_id\" "
            + "WHERE updated.\"clan_member.clan_id\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO clan_member (clan_id, player_character_id, updated) "
            + "SELECT * FROM missing "
            /*
                2 matched clan members(2 parts from different players) can be absent in the db
                do nothing to verify that this is the case, because it will fail otherwise as there will be no second
                 match in filtering phase
             */
            + "ON CONFLICT(player_character_id) DO NOTHING "
        + ") "
        + "SELECT COUNT(*) FROM updated";

    @Autowired
    public ClanMemberDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        super(template, "clan_member", "30 DAYS");
    }

    public void merge(ClanMember... members)
    {
        if(members.length == 0) return;

        List<Object[]> clanMemberData = Arrays.stream(members)
            .map(member->new Object[]{
                member.getClanId(),
                member.getCharacterId()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("clanMembers", clanMemberData);

        getTemplate().query(MERGE_QUERY, params, DAOUtils.INT_EXTRACTOR);
    }

}
