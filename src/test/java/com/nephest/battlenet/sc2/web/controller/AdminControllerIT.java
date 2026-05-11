// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTest;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import com.nephest.battlenet.sc2.web.service.MatchService;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@BlizzardTest
@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
public class AdminControllerIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MatchService matchService;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
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
