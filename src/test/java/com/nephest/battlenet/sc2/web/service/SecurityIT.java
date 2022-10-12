// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@ActiveProfiles({"default", "prod"})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SecurityIT
{

    private static MockMvc mvc;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext
    ) throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity())
            .alwaysDo(print()).build();
    }


    @ParameterizedTest
    @CsvSource
    ({
        "/verify/discord, false",
    })
    public void testNotAuthenticatedSecurity(String path, boolean allowed)
    throws Exception
    {
        mvc.perform
        (
            get(path)
                .with(csrf().asHeader())
        ).andExpect(allowed ? status().isOk() : status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/sba"})
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER})
    public void testUserSecurity(String path) throws Exception
    {
        mvc.perform
        (
            get(path)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        ).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource
    ({
        "/admin, false",
        "/sba/instances/1/actuator/loggers, false",
        "/sba/actuator/info, false",

        "/sba, true",
        "/sba/instances, true",
        "/sba/instances/1, true",
        "/sba/instances/1/actuator/info, true"
    })
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.SERVER_WATCHER})
    public void testServerWatcherSecurity(String path, boolean allowed) throws Exception
    {
        mvc.perform
        (
            get(path)
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf().asHeader())
        //not found instead of ok because SBA server is not deployed in this test
        ).andExpect(allowed ? status().isNotFound() : status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource
    ({
        "/admin, true",
        "/sba/instances/1/actuator/loggers, true",
    })
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.ADMIN})
    public void testAdminSecurity(String path, boolean allowed) throws Exception
    {
        mvc.perform
        (
            get(path)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        //not found instead of ok because SBA server is not deployed in this test
        ).andExpect(allowed ? status().isNotFound() : status().isForbidden());
    }

}
