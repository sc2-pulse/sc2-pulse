// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AccountServiceIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserDetailsService userDetailsService;

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
    public void testSetPassword()
    {
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 2);
        String userName = String.valueOf(accounts[0].getId());

        //accounts without passwords are disabled
        assertThrows
        (
            DisabledException.class,
            ()->userDetailsService.loadUserByUsername(userName)
        );

        String pwd = "password_%123";
        String encodedPwd = accountService.setPassword(accounts[0].getId(), pwd);
        //passwords should be encoded
        assertNotEquals(pwd, encodedPwd);
        assertTrue(accountService.getPasswordEncoder().matches(pwd, encodedPwd));
        //encoded password is saved, not the raw one
        assertEquals(encodedPwd, userDetailsService.loadUserByUsername(userName).getPassword());
    }

    @Test
    public void testGetOrGenerateNewPassword()
    {
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 2);
        String userName = String.valueOf(accounts[0].getId());

        //accounts without passwords are disabled
        assertThrows
        (
            DisabledException.class,
            ()->userDetailsService.loadUserByUsername(userName)
        );
        //new password is generated
        String password = accountService.getOrGenerateNewPassword(accounts[0].getId());
        assertEquals(password, userDetailsService.loadUserByUsername(userName).getPassword());

        //existing password is retrieved
        accountService.getOrGenerateNewPassword(accounts[0].getId());
        assertEquals(password, userDetailsService.loadUserByUsername(userName).getPassword());
    }

}
