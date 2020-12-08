// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class ProPlayerDAO
extends StandardDAO
{

    private static RowMapper<ProPlayer> STD_ROW_MAPPER;

    public static final String STD_SELECT =
        "pro_player.id AS \"pro_player.id\", "
        + "pro_player.revealed_id AS \"pro_player.revealed_id\", "
        + "pro_player.aligulac_id AS \"pro_player.aligulac_id\", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "pro_player.name AS \"pro_player.name\", "
        + "pro_player.country AS \"pro_player.country\", "
        + "pro_player.birthday AS \"pro_player.birthday\", "
        + "pro_player.earnings AS \"pro_player.earnings\", "
        + "pro_player.updated AS \"pro_player.updated\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO pro_player (revealed_id, aligulac_id, nickname, name, country, birthday, earnings, updated) "
        + "VALUES (:revealedId, :aligulacId, :nickname, :name, :country, :birthday, :earnings, :updated)";
    private static final String MERGE_QUERY =
        CREATE_QUERY + " "
        + "ON CONFLICT(revealed_id) DO UPDATE SET "
        + "aligulac_id=excluded.aligulac_id,"
        + "name=excluded.name,"
        + "nickname=excluded.nickname,"
        + "country=excluded.country,"
        + "birthday=excluded.birthday,"
        + "earnings=excluded.earnings,"
        + "updated=excluded.updated";
    private static final String FIND_ALIGULAC_LIST = "SELECT " + STD_SELECT
        + "FROM pro_player WHERE aligulac_id IS NOT NULL";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public  ProPlayerDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        super(template, "pro_player", "30 DAYS");
        this.template = template;
        initMappers();
    }

    private static void initMappers()
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)->
        {
            ProPlayer p = new ProPlayer
            (
                rs.getLong("pro_player.id"),
                rs.getBytes("pro_player.revealed_id"),
                rs.getString("pro_player.nickname"),
                rs.getString("pro_player.name")
            );
            p.setAligulacId(rs.getLong("pro_player.aligulac_id"));
            p.setCountry(rs.getString("pro_player.country"));
            p.setBirthday(rs.getObject("pro_player.birthday", LocalDate.class));
            p.setEarnings(rs.getInt("pro_player.earnings"));
            p.setUpdated(rs.getObject("pro_player.updated", OffsetDateTime.class));
            return p;
        };
    }

    public static RowMapper<ProPlayer> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(ProPlayer proPlayer)
    {
        return new MapSqlParameterSource()
            .addValue("revealedId", proPlayer.getRevealedId())
            .addValue("aligulacId", proPlayer.getAligulacId())
            .addValue("nickname", proPlayer.getNickname())
            .addValue("name", proPlayer.getName())
            .addValue("country", proPlayer.getCountry())
            .addValue("birthday", proPlayer.getBirthday())
            .addValue("earnings", proPlayer.getEarnings())
            .addValue("updated", proPlayer.getUpdated());
    }

    public ProPlayer merge(ProPlayer proPlayer)
    {
        proPlayer.setUpdated(OffsetDateTime.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(proPlayer);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        proPlayer.setId(keyHolder.getKey().longValue());
        return proPlayer;
    }

    public int[] merge(ProPlayer... proPlayers)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[proPlayers.length];
        for(int i = 0; i < proPlayers.length; i++)
        {
            proPlayers[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proPlayers[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

    public List<ProPlayer> findAligulacList()
    {
        return template.query(FIND_ALIGULAC_LIST, getStdRowMapper());
    }

}
