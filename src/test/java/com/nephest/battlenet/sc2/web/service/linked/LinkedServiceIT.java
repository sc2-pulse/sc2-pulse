// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import discord4j.common.util.Snowflake;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
@AutoConfigureMockMvc
public class LinkedServiceIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private DiscordService discordService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    public void testGetLinkedAccounts()
    throws Exception
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#123"));
        DiscordUser discordUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(1L), "discordName", 1))).iterator().next();
        discordService.linkAccountToDiscordUser(acc.getId(), discordUser.getId());
        mvc.perform
        (
            get("/api/account/{id}/linked/external/account", acc.getId())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andReturn();

        discordService.setVisibility(acc.getId(), true);
        Map<SocialMedia, Object> linkedAccounts = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>() {},
            "/api/account/{id}/linked/external/account", acc.getId()
        );
        Assertions.assertThat(linkedAccounts)
            .usingRecursiveComparison()
            .isEqualTo(Map.of(
                SocialMedia.DISCORD,
                Map.of
                (
                    "discriminator", discordUser.getDiscriminator(),
                    "name", discordUser.getName()
                )
            ));
    }

}
