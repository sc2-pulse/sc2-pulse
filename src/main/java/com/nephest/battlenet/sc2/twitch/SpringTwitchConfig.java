// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringTwitchConfig
{

    @Bean
    public TwitchClient twitchClient
    (
        @Value("${twitch.client-id:}") String id,
        @Value("${twitch.client-secret:}") String secret
    )
    {
        return TwitchClientBuilder.builder()
            .withClientId(id)
            .withClientSecret(secret)
            .withEnableHelix(true)
            .build();
    }

}
