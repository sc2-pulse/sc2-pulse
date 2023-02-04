// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import java.time.Duration;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Discord
public class SpringDiscordClient
{

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final GatewayDiscordClient client;

    @Autowired
    public SpringDiscordClient(@Value("${discord.token:}") String token)
    {
        this.client = DiscordClientBuilder.create(token)
            .build()
            .login()
            .block();
    }

    @PreDestroy
    public void destroy()
    {
        client.logout().block(TIMEOUT);
    }

    public GatewayDiscordClient getClient()
    {
        return client;
    }

}
