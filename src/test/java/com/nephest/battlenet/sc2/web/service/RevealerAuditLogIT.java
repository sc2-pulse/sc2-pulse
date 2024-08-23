// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.AccountSecurityContextFactory;
import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AuditLogEntry;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
@AutoConfigureMockMvc
public class RevealerAuditLogIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    private static OffsetDateTime BEFORE_ALL;
    private static final AuditLogEntry[] USER1_ENTRIES = new AuditLogEntry[]{
        new AuditLogEntry
        (
            5L,
            null,
            "public", "pro_player_account",
            AuditLogEntry.Action.DELETE,
            "{\"protected\": null, \"account_id\": 5, \"pro_player_id\": 2, "
                + "\"revealer_account_id\": 1}",
            null,
            1L
        ),
        new AuditLogEntry
        (
            4L,
            null,
            "public", "pro_player_account",
            AuditLogEntry.Action.INSERT,
            "{\"protected\": null, \"account_id\": 5, \"pro_player_id\": 2, "
                + "\"revealer_account_id\": 1}",
            null,
            1L
        )
    };

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired SeasonGenerator generator,
        @Autowired ProPlayerDAO proPlayerDAO,
        @Autowired AccountSecurityContextFactory accountSecurityContextFactory
    )
    throws Exception
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
        BEFORE_ALL = SC2Pulse.offsetDateTime();
        generator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );
        ProPlayer[] players = new ProPlayer[]
        {
            new ProPlayer(null, 1L, "tag1", "name1"),
            new ProPlayer(null, 2L, "tag2", "name2"),
            new ProPlayer(null, 3L, "tag3", "name3"),
        };
        proPlayerDAO.mergeWithoutIds(players);
        User user = new AccountUser(new Account(1L, Partition.GLOBAL, "tag#1"), "pass",
            List.of(SC2PulseAuthority.USER, SC2PulseAuthority.REVEALER));
        mvc.perform
        (
            post("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user(user))
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn();
        mvc.perform
        (
            delete("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user(user))
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn();
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
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testCursorNavigation()
    throws Exception
    {
        AuditLogEntry[] entries1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param("limit", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries1)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries1)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(USER1_ENTRIES);

        AuditLogEntry[] entries2 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param("limit", "1")
                .param("createdCursor", entries1[1].getCreated().toString())
                .param("idCursor", String.valueOf(entries1[1].getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries2)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries2)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(new AuditLogEntry[]{
                new AuditLogEntry
                (
                    3L,
                    null,
                    "public", "pro_player",
                    AuditLogEntry.Action.INSERT,
                    "{\"id\": 3, \"name\": \"name3\", \"team\": null, \"country\": null, "
                        + "\"birthday\": null, \"earnings\": null, \"nickname\": \"tag3\", "
                        + "\"aligulac_id\": 3}",
                    null,
                    null
                )
            });
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testExcludeSystemAuthorFilter()
    throws Exception
    {
        AuditLogEntry[] entries1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param("excludeSystemAuthor", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries1)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries1)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(USER1_ENTRIES);
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testAuthorAccountIdFilter()
    throws Exception
    {
        AuditLogEntry[] entries1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param("authorAccountId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries1)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries1)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(USER1_ENTRIES);
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testActionFilter()
    throws Exception
    {
        AuditLogEntry[] entries1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param
                (
                    "action",
                    mvcConversionService.convert(AuditLogEntry.Action.DELETE, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries1)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries1)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(new AuditLogEntry[]{USER1_ENTRIES[0]});
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testAccountIdFilter()
    throws Exception
    {
        AuditLogEntry[] entries1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/log")
                .param("accountId", "5")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), AuditLogEntry[].class);
        for(AuditLogEntry entry : entries1)
            assertTrue(entry.getCreated().isAfter(BEFORE_ALL));
        Assertions.assertThat(entries1)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .isEqualTo(USER1_ENTRIES);
    }


}
