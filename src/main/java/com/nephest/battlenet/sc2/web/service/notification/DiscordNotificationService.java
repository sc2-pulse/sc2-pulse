// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.notification;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Notification;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Discord @Service
public class DiscordNotificationService
implements NotificationSender
{

    private final DiscordUserDAO discordUserDAO;
    private final DiscordAPI discordAPI;

    @Autowired
    public DiscordNotificationService(DiscordUserDAO discordUserDAO, DiscordAPI discordAPI)
    {
        this.discordUserDAO = discordUserDAO;
        this.discordAPI = discordAPI;
    }

    @Override
    public Mono<Notification> send(Notification notification)
    {
        return discordUserDAO.findByAccountId(notification.getAccountId(), false)
            .map(u->discordAPI.sendDM(DiscordBootstrap.trimIfLong(notification.getMessage()),
                u.getId()).next().map(m->notification))
            .orElse(Mono.just(notification));
    }

}
