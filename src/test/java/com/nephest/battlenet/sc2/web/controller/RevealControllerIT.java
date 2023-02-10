// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class RevealControllerIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private SeasonGenerator generator;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    public void beforeEach
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
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity())
            .alwaysDo(print()).build();
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
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER, SC2PulseAuthority.REVEALER
        }
    )
    public void testAccountRevealing()
    throws Exception
    {
        generator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );

        ProPlayer[] playersFound1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ProPlayer[].class);
        assertEquals(0, playersFound1.length);

        ProPlayer[] players = new ProPlayer[]
        {
            new ProPlayer(null, 1L, "tag1", "name1"),
            new ProPlayer(null, 2L, "tag2", "name2"),
            new ProPlayer(null, 3L, "tag3", "name3"),
        };
        proPlayerDAO.mergeWithoutIds(players);

        ProPlayer[] playersFound2 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ProPlayer[].class);
        assertEquals(players.length, playersFound2.length);
        for(int i = 0; i < players.length; i++) assertEquals(players[i], playersFound2[i]);

        mvc.perform
        (
            post("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        ProPlayerAccount proPlayerAccount = proPlayerAccountDAO.findByProPlayerId(2).get(0);
        assertEquals(5L, proPlayerAccount.getAccountId());
        assertEquals(1L, proPlayerAccount.getRevealerAccountId());

        mvc.perform
        (
            delete("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(proPlayerAccountDAO.findByProPlayerId(2).isEmpty());
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "name")
    public void whenNotRevealer_thenForbidden()
    throws Exception
    {
        mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden());
        mvc.perform
        (
            post("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isForbidden());
        mvc.perform
        (
            delete("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isForbidden());
    }

}
