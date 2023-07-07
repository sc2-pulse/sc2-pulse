// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ProPlayerIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

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
    public void whenNotExists_thenInsert()
    {
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        proPlayerDAO.merge(new ProPlayer(null, 2L, "tag", "name"));

        assertEquals(2, proPlayerDAO.findAll().size());
    }


    @Test
    public void whenExists_thenUpdate()
    {
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag1", "name1"));
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag2", "name2"));
        ProPlayer lastPlayer = proPlayerDAO.merge(new ProPlayer(
            null,
            1L,
            "tag3",
            "name3",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            100 //supplied version doesn't matter
        ));

        List<ProPlayer> proPlayers = proPlayerDAO.findAll();
        assertEquals(1, proPlayers.size());
        ProPlayer proPlayer = proPlayers.get(0);
        assertEquals(lastPlayer.getNickname(), proPlayer.getNickname());
        assertEquals(lastPlayer.getName(), proPlayer.getName());
        assertEquals(lastPlayer.getCountry(), proPlayer.getCountry());
        assertEquals(lastPlayer.getBirthday(), proPlayer.getBirthday());
        assertEquals(lastPlayer.getEarnings(), proPlayer.getEarnings());
        assertTrue(lastPlayer.getUpdated().isEqual(proPlayer.getUpdated()));
        assertEquals(3, proPlayer.getVersion());
    }

    @Test
    public void whenExistsWithSameData_thenDontUpdateVersion()
    {
        ProPlayer lastPlayer = proPlayerDAO.merge(new ProPlayer(
            null,
            1L,
            "tag3",
            "name3",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            1
        ));
        proPlayerDAO.merge(lastPlayer);

        List<ProPlayer> proPlayers = proPlayerDAO.findAll();
        assertEquals(1, proPlayers.size());
        ProPlayer proPlayer = proPlayers.get(0);
        assertEquals(lastPlayer.getVersion(), proPlayer.getVersion());
    }



}
