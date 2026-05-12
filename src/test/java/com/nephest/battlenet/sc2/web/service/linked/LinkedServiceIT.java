// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.discord.DiscordTest;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import discord4j.common.util.Snowflake;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@DiscordTest
@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@AutoConfigureMockMvc
@AutoConfigureDatabase
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
