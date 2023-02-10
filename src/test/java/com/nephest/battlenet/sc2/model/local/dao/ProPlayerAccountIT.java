// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
public class ProPlayerAccountIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

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
    public void testProtectedFlag()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 2L, 1, "name"));

        ProPlayer proPlayer1 = proPlayerDAO.merge(new ProPlayer(null, 1L, "nick1", "name"));
        ProPlayer proPlayer2 = proPlayerDAO.merge(new ProPlayer(null, 2L, "nick2", "name2"));
        proPlayerAccountDAO.merge
        (
            false,
            new ProPlayerAccount(proPlayer1.getId(), acc1.getId(), OffsetDateTime.now(), true)
        );

        //protected links are not updated
        proPlayerAccountDAO.merge
        (
            true,
            new ProPlayerAccount(proPlayer2.getId(), acc1.getId(), OffsetDateTime.now(), false)
        );
        proPlayerAccountDAO.link(proPlayer2.getId(), "tag1");
        proPlayerAccountDAO.link(proPlayer2.getId(), 1L);
        ProPlayerAccount proPlayerAccount1 = template
            .queryForObject("SELECT " + ProPlayerAccountDAO.STD_SELECT
                    + " FROM pro_player_account", ProPlayerAccountDAO.getStdRowMapper());
        assertEquals(proPlayer1.getId(), proPlayerAccount1.getProPlayerId());
        assertTrue(proPlayerAccount1.isProtected());

        //remove protected flag
        proPlayerAccountDAO.merge
        (
            false,
            new ProPlayerAccount(proPlayer1.getId(), acc1.getId(), OffsetDateTime.now(), false)
        );

        //unprotected links are updated
        proPlayerAccountDAO.link(proPlayer2.getId(), "tag1");
        proPlayerAccountDAO.link(proPlayer2.getId(), 1L);
        ProPlayerAccount proPlayerAccount2 = template
            .queryForObject("SELECT " + ProPlayerAccountDAO.STD_SELECT
                + " FROM pro_player_account", ProPlayerAccountDAO.getStdRowMapper());
        assertEquals(proPlayer2.getId(), proPlayerAccount2.getProPlayerId());
        assertFalse(proPlayerAccount2.isProtected());
    }

}
