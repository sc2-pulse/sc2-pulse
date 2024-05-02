// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.web.service.MatchService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AdminControllerIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MatchService matchService;

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
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void whenMatchUpdateFrameDurationIsNegative_thenBadRequest()
    throws Exception
    {
        long lowDuration = -1;
        mvc.perform
        (
            post("/admin/update/match/frame/{lowDuration}", lowDuration)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Duration can't be negative"));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSetMatchUpdateFrameDuration()
    throws Exception
    {
        Duration newDuration = MatchService.MATCH_UPDATE_FRAME.plusMillis(1);
        mvc.perform
        (
            post("/admin/update/match/frame/{newDuration}", newDuration.toMillis())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk());
        assertEquals(newDuration, matchService.getMatchUpdateFrame());
    }

}
