// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.rest.request.BucketGlobalRateLimiter;
import discord4j.rest.request.GlobalRateLimiter;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Discord
public class SpringDiscordClient
{

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final GatewayDiscordClient client;
    private final GlobalRateLimiter globalRateLimiter = BucketGlobalRateLimiter.create();

    @Autowired
    public SpringDiscordClient(@Value("${discord.token:}") String token)
    {
        this.client = DiscordClientBuilder.create(token)
            .setGlobalRateLimiter(globalRateLimiter)
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

    public GlobalRateLimiter getGlobalRateLimiter()
    {
        return globalRateLimiter;
    }

}
