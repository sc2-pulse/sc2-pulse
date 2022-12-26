// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.notification;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Notification;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import discord4j.core.object.entity.Message;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class DiscordNotificationServiceTest
{

    @Mock
    private DiscordUserDAO discordUserDAO;

    @Mock
    private DiscordAPI discordAPI;

    private DiscordNotificationService service;

    @BeforeEach
    public void beforeEach()
    {
        service = new DiscordNotificationService(discordUserDAO, discordAPI);
    }

    @Test
    public void whenDiscordUserNotFound_thenTreatItIsAsSuccessfullySentMessage()
    {
        when(discordUserDAO.findByAccountId(1L, false))
            .thenReturn(Optional.empty());

        Notification notification = new Notification(1L, "msg");
        Notification notificationSent = service.send(notification).block();
        assertNotNull(notificationSent);
        assertEquals(notification, notificationSent);
    }

    @Test
    public void whenDiscordUserIsFound_thenSendMessage()
    {
        when(discordUserDAO.findByAccountId(1L, false))
            .thenReturn(Optional.of(new DiscordUser(2L, "name", 123)));
        Message msg = mock(Message.class);
        when(discordAPI.sendDM("msg", 2L)).thenReturn(Flux.just(msg));

        Notification notification = new Notification(1L, "msg");
        Notification notificationSent = service.send(notification).block();
        assertNotNull(notificationSent);
        assertEquals(notification, notificationSent);
    }

}
