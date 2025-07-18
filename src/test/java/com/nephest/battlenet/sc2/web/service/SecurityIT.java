// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {AllTestConfig.class})
@ActiveProfiles({"default", "prod", "test"})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SecurityIT
{

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO
    ) throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        accountDAO.merge(new Account(1L, Partition.GLOBAL, "user"));
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }


    @ParameterizedTest
    @CsvSource
    ({
        "GET, /verify/discord, 401",
        "GET, /settings/advanced, 401",
    })
    public void testNotAuthenticatedSecurity(String method, String path, int status)
    throws Exception
    {
        WebServiceTestUtil.executeAndExpect(mvc, method, path, status);
    }

    @ParameterizedTest
    @CsvSource
    ({
        "GET, /admin, 403",
        "GET, /settings/advanced, 200",
    })
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER})
    public void testUserSecurity(String method, String path, int status)
    throws Exception
    {
        WebServiceTestUtil.executeAndExpect(mvc, method, path, MediaType.APPLICATION_JSON, status);
    }

    @ParameterizedTest
    @CsvSource
    ({
        "GET, /settings/advanced, 401",
        "DELETE, /settings/advanced, 401",
        "GET, /data/battle-net, 401",
        "POST, /data/battle-net, 401"
    })
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles = {SC2PulseAuthority.USER},
        authorizedClientRegistrationId = WithBlizzardMockUser.NON_BLIZZARD_OAUTH_REGISTRATION_ID
    )
    public void testFullyAuthorizedNonBlizzardSecurity(String method, String path, int status)
    throws Exception
    {
        WebServiceTestUtil.executeAndExpect(mvc, method, path, status);
    }

    @ParameterizedTest
    @CsvSource
    ({
        "GET, /admin, 403",
    })
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.SERVER_WATCHER})
    public void testServerWatcherSecurity(String method, String path, int status)
    throws Exception
    {
        WebServiceTestUtil.executeAndExpect(mvc, method, path, MediaType.APPLICATION_JSON, status);
    }

    @ParameterizedTest
    @CsvSource
    ({
        "GET, /admin, 404",
    })
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.ADMIN})
    public void testAdminSecurity(String method, String path, int status)
    throws Exception
    {
        WebServiceTestUtil.executeAndExpect(mvc, method, path, MediaType.APPLICATION_JSON, status);
    }

}
