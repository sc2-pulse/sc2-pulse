// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import discord4j.core.GatewayDiscordClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "discord", name = "token")
public class SpringDiscordClient
{

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final GatewayDiscordClient client;

    public SpringDiscordClient
    (
        List<SlashCommand> handlers,
        @Value("${discord.token:}") String token,
        @Value("${discord.guild:}") Long guild
    )
    {
        this.client = DiscordBootstrap.load(handlers, token, guild);
    }

    @PreDestroy
    public void destroy()
    {
        client.logout().block(TIMEOUT);
    }

}
