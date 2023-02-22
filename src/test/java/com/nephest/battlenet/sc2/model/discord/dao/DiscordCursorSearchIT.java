// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.web.service.DiscordIT;
import discord4j.common.util.Snowflake;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class DiscordCursorSearchIT
{

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired DiscordUserDAO discordUserDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            discordUserDAO.merge
            (
                new DiscordUser(Snowflake.of(1L), "name1", 1),
                new DiscordUser(Snowflake.of(2L), "name2", 2),
                new DiscordUser(Snowflake.of(3L), "name3", 3)
            );
        }
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }

    }

    @Test
    public void testFullIdCursor()
    {
        List<DiscordUser> users1 = discordUserDAO.findByIdCursor(Snowflake.of(0L), 1);
        assertEquals(1, users1.size());
        DiscordIT.verifyStdDiscordUser(users1.get(0), 1);

        List<DiscordUser> users2 = discordUserDAO.findByIdCursor(Snowflake.of(1L), 2);
        assertEquals(2, users2.size());
        DiscordIT.verifyStdDiscordUser(users2.get(0), 2);
        DiscordIT.verifyStdDiscordUser(users2.get(1), 3);

        assertTrue(discordUserDAO.findByIdCursor(Snowflake.of(3L), 100).isEmpty());
    }

    @Test
    public void testIdIdCursor()
    {
        List<Snowflake> ids1 = discordUserDAO.findIdsByIdCursor(Snowflake.of(0L), 1);
        assertEquals(1, ids1.size());
        assertEquals(ids1.get(0), Snowflake.of(1));

        List<Snowflake> ids2 = discordUserDAO.findIdsByIdCursor(Snowflake.of(1L), 2);
        assertEquals(2, ids2.size());
        assertEquals(ids2.get(0), Snowflake.of(2));
        assertEquals(ids2.get(1), Snowflake.of(3));

        assertTrue(discordUserDAO.findByIdCursor(Snowflake.of(3L), 100).isEmpty());
    }

}
