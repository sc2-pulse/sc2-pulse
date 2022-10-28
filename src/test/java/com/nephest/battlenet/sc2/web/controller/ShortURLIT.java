// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ShortURLIT
{

    private MockMvc mvc;

    @Value("${discord.bot.invite.url}")
    private String discordBotInviteUrl;

    @BeforeEach
    public void beforeEach
    (
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
    }

    @Test
    public void testInviteDiscordBotUrl()
    throws Exception
    {
        mvc.perform(get("/discord/bot/invite"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", discordBotInviteUrl));
    }

}
