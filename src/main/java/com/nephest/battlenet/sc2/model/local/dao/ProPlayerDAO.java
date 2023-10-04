// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProPlayerDAO
{

    private static RowMapper<ProPlayer> STD_ROW_MAPPER;

    public static final String STD_SELECT =
        "pro_player.id AS \"pro_player.id\", "
        + "pro_player.aligulac_id AS \"pro_player.aligulac_id\", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "pro_player.name AS \"pro_player.name\", "
        + "pro_player.country AS \"pro_player.country\", "
        + "pro_player.birthday AS \"pro_player.birthday\", "
        + "pro_player.earnings AS \"pro_player.earnings\", "
        + "pro_player.updated AS \"pro_player.updated\", "
        + "pro_player.version AS \"pro_player.version\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO pro_player (aligulac_id, nickname, name, country, birthday, earnings, updated) "
        + "VALUES (:aligulacId, :nickname, :name, :country, :birthday, :earnings, :updated)";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES(:aligulacId, :nickname, :name, :country, :birthday, :earnings, :updated)), "
        + "existing AS "
        + "("
            + "SELECT id "
            + "FROM vals v (aligulac_id, nickname, name, country, birthday, earnings, updated) "
            + "INNER JOIN pro_player USING(aligulac_id) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE pro_player "
            + "SET "
            + "aligulac_id=v.aligulac_id, "
            + "name=v.name, "
            + "nickname=v.nickname, "
            + "country=v.country, "
            + "birthday=v.birthday, "
            + "earnings=v.earnings, "
            + "updated=v.updated "
            + "FROM vals v (aligulac_id, nickname, name, country, birthday, earnings, updated) "
            + "WHERE pro_player.aligulac_id = v.aligulac_id "
            + "AND "
            + "( "
                + "pro_player.name IS DISTINCT FROM v.name "
                + "OR pro_player.nickname IS DISTINCT FROM v.nickname "
                + "OR pro_player.country IS DISTINCT FROM v.country "
                + "OR pro_player.birthday IS DISTINCT FROM v.birthday "
                + "OR pro_player.earnings IS DISTINCT FROM v.earnings "
            + ") "
            + "RETURNING id "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO pro_player (aligulac_id, nickname, name, country, birthday, earnings, updated) "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM existing) "
            + "ON CONFLICT(aligulac_id) DO UPDATE SET "
            + "aligulac_id=excluded.aligulac_id,"
            + "name=excluded.name,"
            + "nickname=excluded.nickname,"
            + "country=excluded.country,"
            + "birthday=excluded.birthday,"
            + "earnings=excluded.earnings,"
            + "updated=excluded.updated "
            + "RETURNING id "
        + ") "
        + "SELECT id FROM existing "
        + "UNION "
        + "SELECT id FROM inserted";

    public static final String MERGE_VERSIONED =
        "WITH updated AS "
        + "("
            + "UPDATE pro_player "
            + "SET name = :name, " 
            + "nickname = :nickname, "
            + "country = :country, "
            + "birthday = :birthday, "
            + "earnings = :earnings, "
            + "updated = :updated "
            + "WHERE id = :id "
            + "AND version = :version "
            + "RETURNING id, version "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO pro_player (nickname, name, country, birthday, earnings, updated) "
            + "SELECT :nickname, :name, :country, :birthday, :earnings, :updated "
            + "WHERE :id IS NULL "
            + "RETURNING id, version "
        + ") "
        + "SELECT id, version FROM updated "
        + "UNION "
        + "SELECT id, version FROM inserted";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT
        + "FROM pro_player "
        + "WHERE id IN(:ids) "
        + "ORDER BY id";

    private static final String FIND_ALIGULAC_LIST = "SELECT " + STD_SELECT
        + "FROM pro_player WHERE aligulac_id IS NOT NULL";

    private static final String FIND_ALL = "SELECT " + STD_SELECT + " FROM pro_player ORDER BY id";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public  ProPlayerDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
        initMappers();
    }

    private static void initMappers()
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new ProPlayer
        (
            rs.getLong("pro_player.id"),
            DAOUtils.getLong(rs, "pro_player.aligulac_id"),
            rs.getString("pro_player.nickname"),
            rs.getString("pro_player.name"),
            rs.getString("pro_player.country"),
            rs.getObject("pro_player.birthday", LocalDate.class),
            DAOUtils.getInteger(rs, "pro_player.earnings"),
            rs.getObject("pro_player.updated", OffsetDateTime.class),
            rs.getInt("pro_player.version")
        );
    }

    public static RowMapper<ProPlayer> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(ProPlayer proPlayer)
    {
        return new MapSqlParameterSource()
            .addValue("aligulacId", proPlayer.getAligulacId(), Types.BIGINT)
            .addValue("nickname", proPlayer.getNickname())
            .addValue("name", proPlayer.getName(), Types.VARCHAR)
            .addValue("country", proPlayer.getCountry(), Types.VARCHAR)
            .addValue("birthday", proPlayer.getBirthday(), Types.DATE)
            .addValue("earnings", proPlayer.getEarnings(), Types.INTEGER)
            .addValue("updated", proPlayer.getUpdated());
    }

    public ProPlayer merge(ProPlayer proPlayer)
    {
        proPlayer.setUpdated(OffsetDateTime.now());
        MapSqlParameterSource params = createParameterSource(proPlayer);
        proPlayer.setId(template.query(MERGE_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return proPlayer;
    }

    public int[] mergeWithoutIds(ProPlayer... proPlayers)
    {
        if(proPlayers.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[proPlayers.length];
        for(int i = 0; i < proPlayers.length; i++)
        {
            proPlayers[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proPlayers[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

    public ProPlayer mergeVersioned(ProPlayer proPlayer)
    {
        proPlayer.setUpdated(OffsetDateTime.now());
        MapSqlParameterSource params = createParameterSource(proPlayer)
            .addValue("id", proPlayer.getId(), Types.BIGINT)
            .addValue("version", proPlayer.getVersion(), Types.INTEGER);
        Long[] pair = template.query(MERGE_VERSIONED, params, DAOUtils.LONG_PAIR_EXTRACTOR);
        if(pair[0] == null) throw new OptimisticLockingFailureException("Invalid version: " + proPlayer.getVersion());

        proPlayer.setVersion(pair[1].intValue());
        proPlayer.setId(pair[0]);
        return proPlayer;
    }

    public List<ProPlayer> find(Set<Long> ids)
    {
        if(ids.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

    public List<ProPlayer> findAligulacList()
    {
        return template.query(FIND_ALIGULAC_LIST, getStdRowMapper());
    }

    public List<ProPlayer> findAll()
    {
        return template.query(FIND_ALL, getStdRowMapper());
    }

}
