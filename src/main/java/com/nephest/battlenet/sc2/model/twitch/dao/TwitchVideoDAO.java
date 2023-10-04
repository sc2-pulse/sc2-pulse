// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch.dao;

import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.StandardDAO;
import com.nephest.battlenet.sc2.model.twitch.TwitchVideo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TwitchVideoDAO
extends StandardDAO
{
    
    public static final String STD_SELECT = 
        "twitch_video.id AS \"twitch_video.id\", "
        + "twitch_video.twitch_user_id AS \"twitch_video.twitch_user_id\", "
        + "twitch_video.url AS \"twitch_video.url\", "
        + "twitch_video.begin AS \"twitch_video.begin\", "
        + "twitch_video.\"end\" AS \"twitch_video.end\" ";
    

    private static final String MERGE = "WITH "
        + "vals AS (VALUES :videos), "
        + "updated AS "
        + "("
            + "UPDATE twitch_video "
            + "SET url = v.url, "
            + "begin = v.begin, "
            + "\"end\" = v.\"end\" "
            + "FROM vals v(id, twitch_user_id, url, begin, \"end\") "
            + "WHERE twitch_video.id = v.id "
            + "RETURNING twitch_video.id "
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.id, v.twitch_user_id, v.url, v.begin, v.\"end\" "
            + "FROM vals v(id, twitch_user_id, url, begin, \"end\") "
            + "LEFT JOIN twitch_video USING(id) "
            + "WHERE twitch_video.id IS null"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO twitch_video(id, twitch_user_id, url, begin, \"end\") "
            + "SELECT * FROM missing "
            + "ON CONFLICT DO NOTHING "
            + "RETURNING id "
        + ") "
        + "SELECT * FROM updated "
        + "UNION "
        + "SELECT * FROM inserted";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT + "FROM twitch_video WHERE id IN(:ids)";

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<TwitchVideo> STD_ROW_MAPPER = (rs, i)->new TwitchVideo
    (
        rs.getLong("twitch_video.id"),
        rs.getLong("twitch_video.twitch_user_id"),
        rs.getString("twitch_video.url"),
        rs.getObject("twitch_video.begin", OffsetDateTime.class),
        rs.getObject("twitch_video.end", OffsetDateTime.class)
    );

    @Autowired
    public TwitchVideoDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        super(template, "twitch_video", "\"end\"", MatchDAO.TTL_DAYS + " DAYS");
        this.template = template;
    }

    public Set<TwitchVideo> merge(Set<TwitchVideo> videos)
    {
        if(videos.isEmpty()) return videos;

        List<Object[]> data = videos.stream()
            .map(v->new Object[]
            {
                v.getId(),
                v.getTwitchUserId(),
                v.getUrl(),
                v.getBegin(),
                v.getEnd()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("videos", data);
        template.queryForList(MERGE, params, Long.class);
        return videos;
    }

    public List<TwitchVideo> findById(Set<Long> ids)
    {
        if(ids.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

}
