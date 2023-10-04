// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountProperty;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
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
public class AccountPropertyIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private AccountPropertyDAO accountPropertyDAO;

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
    public void testChain()
    {
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 3);
        accountPropertyDAO.merge(Set.of(
            new AccountProperty(accounts[0].getId(), AccountProperty.PropertyType.PASSWORD, "p1"),
            new AccountProperty(accounts[1].getId(), AccountProperty.PropertyType.PASSWORD, "p2")
        ));

        AccountProperty property1 = accountPropertyDAO
            .find(accounts[0].getId(), AccountProperty.PropertyType.PASSWORD)
            .orElseThrow();
        verify(property1, accounts[0].getId(), AccountProperty.PropertyType.PASSWORD, "p1");

        AccountProperty property2 = accountPropertyDAO
            .find(accounts[1].getId(), AccountProperty.PropertyType.PASSWORD)
            .orElseThrow();
        verify(property2, accounts[1].getId(), AccountProperty.PropertyType.PASSWORD, "p2");

        accountPropertyDAO.merge(Set.of(
            //updated
            new AccountProperty(accounts[0].getId(), AccountProperty.PropertyType.PASSWORD, "p11"),
            //inserted
            new AccountProperty(accounts[2].getId(), AccountProperty.PropertyType.PASSWORD, "p3")
        ));

        AccountProperty property11 = accountPropertyDAO
            .find(accounts[0].getId(), AccountProperty.PropertyType.PASSWORD)
            .orElseThrow();
        verify(property11, accounts[0].getId(), AccountProperty.PropertyType.PASSWORD, "p11");

        AccountProperty property3 = accountPropertyDAO
            .find(accounts[2].getId(), AccountProperty.PropertyType.PASSWORD)
            .orElseThrow();
        verify(property3, accounts[2].getId(), AccountProperty.PropertyType.PASSWORD, "p3");
    }

    private void verify
    (
        AccountProperty accountProperty,
        Long accountId,
        AccountProperty.PropertyType type,
        String value
    )
    {
        assertEquals(accountId, accountProperty.getAccountId());
        assertEquals(type, accountProperty.getType());
        assertEquals(value, accountProperty.getValue());
    }

}
