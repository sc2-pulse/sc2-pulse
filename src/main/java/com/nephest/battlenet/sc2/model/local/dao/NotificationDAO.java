// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.Notification;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDAO
extends StandardDAO
{

    public static final String STD_SELECT =
        "notification.id AS \"notification.id\", "
        + "notification.account_id AS \"notification.account_id\", "
        + "notification.message AS \"notification.message\", "
        + "notification.created AS \"notification.created\" ";

    public static final RowMapper<Notification> STD_ROW_MAPPER = (rs, i)->new Notification
    (
        rs.getLong("notification.id"),
        rs.getLong("notification.account_id"),
        rs.getString("notification.message"),
        rs.getObject("notification.created", OffsetDateTime.class)
    );

    private static final String CREATE =
        "INSERT INTO notification(account_id, message) "
        + "VALUES(:accountId, :message)";

    private static final String DELETE_BY_ID =
        "DELETE FROM notification WHERE id IN(:ids)";

    private static final String FIND_ALL = "SELECT " + STD_SELECT + " FROM notification";

    @Autowired
    public NotificationDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        super(template, "notification", "created", "6 HOURS");
    }

    public int[] create(String message, Long... recipientAccountIds)
    {
        if(recipientAccountIds.length == 0) return new int[0];

        MapSqlParameterSource[] params = new MapSqlParameterSource[recipientAccountIds.length];
        for(int i = 0; i < recipientAccountIds.length; i++)
            params[i] = new MapSqlParameterSource()
                .addValue("accountId", recipientAccountIds[i])
                .addValue("message", message);
        return getTemplate().batchUpdate(CREATE, params);
    }

    public List<Notification> findAll()
    {
        return getTemplate().query(FIND_ALL, STD_ROW_MAPPER);
    }

    public int removeByIds(Set<Long> ids)
    {
        if(ids.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return getTemplate().update(DELETE_BY_ID, params);
    }

}
