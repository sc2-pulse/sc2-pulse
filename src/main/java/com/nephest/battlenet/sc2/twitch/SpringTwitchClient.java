// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.twitch;

import com.github.twitch4j.TwitchClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SpringTwitchClient
{

    private final TwitchClient twitchClient;

    @Autowired
    public SpringTwitchClient(TwitchClient twitchClient)
    {
        this.twitchClient = twitchClient;
    }

    @PreDestroy
    public void destroy()
    {
        twitchClient.close();
    }

}
