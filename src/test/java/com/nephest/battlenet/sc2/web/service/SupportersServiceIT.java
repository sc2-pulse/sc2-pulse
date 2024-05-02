// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SupportersServiceIT
{

    private static MockMvc mvc;

    @Autowired
    private SupporterService supporterService;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
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
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSupporterManipulation() throws Exception
    {
        mvc.perform
        (
            post("/admin/supporters/supporter/{name}", "sup1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        ).andExpect(status().isOk());
        assertTrue(supporterService.getSupporters().contains("sup1"));

        mvc.perform
        (
            delete("/admin/supporters/supporter/{name}", "sup1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        ).andExpect(status().isOk());
        assertFalse(supporterService.getSupporters().contains("sup1"));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testDonorsManipulation() throws Exception
    {
        mvc.perform
        (
            post("/admin/supporters/donor/{name}", "don1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        ).andExpect(status().isOk());
        assertTrue(supporterService.getDonors().contains("don1"));

        mvc.perform
        (
            delete("/admin/supporters/donor/{name}", "don1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        ).andExpect(status().isOk());
        assertFalse(supporterService.getDonors().contains("don1"));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER})
    public void testDonorsSecurity() throws Exception
    {
        mvc.perform
        (
            post("/admin/supporters/donor/{name}", "don1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        ).andExpect(status().isForbidden());
    }

}
