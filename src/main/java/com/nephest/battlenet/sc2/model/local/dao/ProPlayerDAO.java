// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProPlayerDAO
extends StandardDAO
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
        + "pro_player.updated AS \"pro_player.updated\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO pro_player (aligulac_id, nickname, name, country, birthday, earnings, updated) "
        + "VALUES (:aligulacId, :nickname, :name, :country, :birthday, :earnings, :updated)";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES(:aligulacId, :nickname, :name, :country, :birthday, :earnings, :updated)), "
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
            + "RETURNING id "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO pro_player (aligulac_id, nickname, name, country, birthday, earnings, updated) "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM updated) "
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
        + "SELECT id FROM updated "
        + "UNION "
        + "SELECT id FROM inserted";
    private static final String FIND_ALIGULAC_LIST = "SELECT " + STD_SELECT
        + "FROM pro_player WHERE aligulac_id IS NOT NULL";

    private static final String FIND_ALL = "SELECT " + STD_SELECT + " FROM pro_player ORDER BY id";

    private static final String LINK_TWITCH_USERS = "UPDATE pro_player "
        + "SET twitch_user_id = twitch_user.id "
        + "FROM pro_player pp "
        + "INNER JOIN social_media_link ON pp.id = social_media_link.pro_player_id "
            + "AND social_media_link.type = :twitchMediaType "
        + "INNER JOIN twitch_user ON LOWER(reverse(split_part(reverse(social_media_link.url), '/', 1))) = LOWER(twitch_user.login) "
        + "WHERE pro_player.id = pp.id "
        + "AND pro_player.twitch_user_id IS DISTINCT FROM twitch_user.id";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public  ProPlayerDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "pro_player", "30 DAYS");
        this.template = template;
        this.conversionService = conversionService;
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
            rs.getObject("pro_player.updated", OffsetDateTime.class)
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

    public List<ProPlayer> findAligulacList()
    {
        return template.query(FIND_ALIGULAC_LIST, getStdRowMapper());
    }

    public List<ProPlayer> findAll()
    {
        return template.query(FIND_ALL, getStdRowMapper());
    }

    public int linkTwitchUsers()
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("twitchMediaType", conversionService.convert(SocialMedia.TWITCH, Integer.class));
        return template.update(LINK_TWITCH_USERS, params);
    }

}
