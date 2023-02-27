// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.SpringDiscordClient;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Discord
@Component
public class DiscordOAuth2RateLimiter
implements OAuth2RateLimiter
{

    private final SpringDiscordClient springDiscordClient;

    @Autowired
    public DiscordOAuth2RateLimiter(SpringDiscordClient springDiscordClient)
    {
        this.springDiscordClient = springDiscordClient;
    }

    @Override
    public String getClientRegistrationId()
    {
        return DiscordAPI.USER_CLIENT_REGISTRATION_ID;
    }

    @Override
    public <T> Flux<T> withLimiter(Publisher<T> publisher, boolean localLimiter)
    {
       return springDiscordClient.getGlobalRateLimiter().withLimiter(publisher);
    }

}
