// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Notification;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class NotificationIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private NotificationDAO notificationDAO;

    @Autowired
    private JdbcTemplate template;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testLifecycleChain()
    {
        Account account1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account account2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        final String msg = "msg";
        assertArrayEquals
        (
            new int[]{-2, -2},
            notificationDAO.create(msg, account1.getId(), account2.getId())
        );
        assertArrayEquals
        (
            new int[]{1},
            notificationDAO.create(msg, account1.getId())
        );
        List<Notification> notifications = notificationDAO.findAll();
        assertEquals(3, notifications.size());
        notifications.sort(Comparator.comparing(Notification::getAccountId));

        Notification notification1 = notifications.get(0);
        assertEquals(account1.getId(), notification1.getAccountId());
        assertEquals(msg, notification1.getMessage());

        Notification notification2 = notifications.get(1);
        assertEquals(account1.getId(), notification2.getAccountId());
        assertEquals(msg, notification2.getMessage());

        Notification notification3 = notifications.get(2);
        assertEquals(account2.getId(), notification3.getAccountId());
        assertEquals(msg, notification3.getMessage());

        assertEquals(2, notificationDAO
            .removeByIds(Set.of(notification1.getId(), notification3.getId())));
        List<Notification> notificationsAfterRemoval = notificationDAO.findAll();
        assertEquals(1, notificationsAfterRemoval.size());
        assertEquals(notification2.getId(), notificationsAfterRemoval.get(0).getId());
    }

    @Test
    public void testRemoveExpired()
    {
        Account account1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        assertArrayEquals
        (
            new int[]{1},
            notificationDAO.create("msg", account1.getId())
        );
        assertArrayEquals
        (
            new int[]{1},
            notificationDAO.create("msg", account1.getId())
        );

        assertEquals(1, template.update(
            "UPDATE notification SET created = NOW() - INTERVAL '6 hours' WHERE id = 1"));
        assertEquals(1, notificationDAO.removeExpired());
        List<Notification> notifications = notificationDAO.findAll();
        assertEquals(1, notifications.size());
        assertEquals(2L, notifications.get(0).getId());
    }

}
