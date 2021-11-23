// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import discord4j.core.GatewayDiscordClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DiscordConfig
{

    @Bean
    @ConditionalOnProperty(prefix = "discord", name = "token")
    public GatewayDiscordClient gatewayDiscordClient
    (List<SlashCommand> handlers,  @Value("${discord.token:}") String token, @Value("${discord.guild:}") Long guild)
    {
        return DiscordBootstrap.load(handlers, token, guild);
    }

}
