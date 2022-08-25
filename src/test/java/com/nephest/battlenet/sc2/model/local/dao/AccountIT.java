// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.sql.Connection;
import java.sql.SQLException;
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
import org.springframework.test.jdbc.JdbcTestUtils;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AccountIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

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
    public void whenAccountExists_thenReturnIt()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        Account mergedAcc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"), character);
        assertEquals(acc.getId(), mergedAcc.getId());
    }

    @Test
    public void whenCharacterAccountExists_thenUpdateAndReturnIt()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        Account mergedAcc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"), character);
        assertEquals(acc.getId(), mergedAcc.getId());
        Account foundAcc = accountDAO.findByIds(mergedAcc.getId()).get(0);
        //btag is updated
        assertEquals("tag#2", foundAcc.getBattleTag());
        //nothing is inserted
        assertEquals(1, JdbcTestUtils.countRowsInTable(template, "account"));
    }

    @Test
    public void whenAccountNotExists_thenInsertAndReturnIt()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter character2 = new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 2, "name#2");
        Account mergedAcc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"), character2);
        //new account is inserted
        assertNotEquals(acc.getId(), mergedAcc.getId());
    }

}
