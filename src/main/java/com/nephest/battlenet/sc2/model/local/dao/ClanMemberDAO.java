// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ClanMember;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class ClanMemberDAO
{

    public static final Duration TTL = Duration.ofDays(30);

    public static final String STD_SELECT =
        "clan_member.player_character_id AS \"clan_member.player_character_id\", "
        + "clan_member.clan_id AS \"clan_member.clan_id\" ";

    private static final String MERGE =
        "WITH "
        + "vals AS(VALUES :data), "
        + "updated AS "
        + "("
            + "UPDATE clan_member "
            + "SET clan_id = v.clan_id, "
            + "updated = NOW() "
            + "FROM vals v(player_character_id, clan_id) "
            + "WHERE clan_member.player_character_id = v.player_character_id "
            + "RETURNING 1 "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO clan_member(player_character_id, clan_id) "
            + "SELECT * FROM "
            + "("
                + "SELECT v.player_character_id, v.clan_id "
                + "FROM vals v(player_character_id, clan_id) "
                + "LEFT JOIN clan_member USING(player_character_id) "
                + "WHERE clan_member.player_character_id IS NULL"
            + ") AS missing "
            + "RETURNING 1 "
        + ") "
        + "SELECT COUNT(*) FROM updated, inserted";

    private static final String REMOVE_BY_CHARACTER_IDS =
        "DELETE FROM clan_member WHERE player_character_id IN(:playerCharacterIds)";

    private static final String FIND_BY_CHARACTER_IDS =
        "SELECT " + STD_SELECT
        + "FROM clan_member "
        + "WHERE player_character_id IN (:playerCharacterIds)";

    private static final String FIND_BY_CLAN_IDS =
        "SELECT " + STD_SELECT
        + "FROM clan_member "
        + "WHERE clan_id IN (:clanIds)";

    private static final String COUNT_INACTIVE =
        "SELECT COUNT(*) FROM clan_member WHERE updated < :to";

    private static final String REMOVE_EXPIRED =
        "DELETE "
        + "FROM clan_member "
        + "WHERE updated < NOW() - INTERVAL '" + TTL.toDays() + " days' "
        + "RETURNING player_character_id";

    public static RowMapper<ClanMember> STD_ROW_MAPPER =
    (rs, rowNum)->new ClanMember
    (
        rs.getLong("clan_member.player_character_id"),
        rs.getInt("clan_member.clan_id")
    );

    public static ResultSetExtractor<ClanMember> STD_EXTRACTOR =
        DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public ClanMemberDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
       this.template = template;
    }

    public NamedParameterJdbcTemplate getTemplate()
    {
        return template;
    }

    public List<ClanMember> find(Set<Long> playerCharacterIds)
    {
        if(playerCharacterIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", playerCharacterIds);
        return getTemplate().query(FIND_BY_CHARACTER_IDS, params, STD_ROW_MAPPER);
    }

    public List<ClanMember> findByClanIds(Set<Integer> clanIds)
    {
        if(clanIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("clanIds", clanIds);
        return getTemplate().query(FIND_BY_CLAN_IDS, params, STD_ROW_MAPPER);
    }

    public Set<ClanMember> merge(Set<ClanMember> clans)
    {
        if(clans.isEmpty()) return clans;

        List<Object[]> data = clans.stream()
            .filter(Objects::nonNull)
            .map(clan->new Object[]{
                clan.getPlayerCharacterId(),
                clan.getClanId()
            }).collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("data", data);
        getTemplate().query(MERGE, params, DAOUtils.INT_MAPPER);
        return clans;
    }

    public int remove(Set<Long> playerCharacterIds)
    {
        if(playerCharacterIds.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", playerCharacterIds);
        return getTemplate().update(REMOVE_BY_CHARACTER_IDS, params);
    }

    public List<Long> removeExpired()
    {
        return template.queryForList(REMOVE_EXPIRED, Map.of(), Long.class);
    }

    public int getInactiveCount(OffsetDateTime to)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("to", to);
        return getTemplate().queryForObject(COUNT_INACTIVE, params, Integer.class);
    }

}
