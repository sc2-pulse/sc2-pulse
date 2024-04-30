// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.notification;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.Notification;
import com.nephest.battlenet.sc2.model.local.dao.NotificationDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest
{

    @Mock
    private NotificationDAO notificationDAO;

    @Mock
    private NotificationSender notificationSender;

    private NotificationService service;

    @BeforeEach
    public void beforeEach()
    {
        service = new NotificationService(notificationDAO, notificationSender);
    }

    @Test
    public void testSendNotifications()
    {
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        List<Notification> notifications = List.of
        (
            new Notification(1L, 1L, "msg1", SC2Pulse.offsetDateTime()),
            new Notification(2L, 1L, "msg2", SC2Pulse.offsetDateTime())
        );
        when(notificationDAO.findAll()).thenReturn(notifications);
        //1 notification is successfully sent, other throw an exception
        when(notificationSender.send(any())).thenAnswer(inv->{
            Notification n = inv.getArgument(0);
            return n.getId() == 1
                ? Mono.just(n)
                : Mono.error(new IllegalStateException("Test exception"));
        });

        //exceptions are properly handled
        assertEquals(1, service.sendNotifications());
        verify(notificationSender, times(2)).send(notificationCaptor.capture());
        assertTrue(notificationCaptor.getAllValues().containsAll(notifications));
        //only notification that were sent without exceptions are removed
        verify(notificationDAO).removeByIds(argThat(s->s.equals(Set.of(1L))));
    }

}
