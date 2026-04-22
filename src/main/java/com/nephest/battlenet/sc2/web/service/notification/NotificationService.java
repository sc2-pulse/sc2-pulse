// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.notification;

import com.nephest.battlenet.sc2.model.local.Notification;
import com.nephest.battlenet.sc2.model.local.dao.NotificationDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class NotificationService
{

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationDAO notificationDAO;
    private final NotificationSender notificationSender;

    @Autowired
    public NotificationService
    (
        NotificationDAO notificationDAO,
        @Autowired(required = false) NotificationSender notificationSender
    )
    {
        this.notificationDAO = notificationDAO;
        this.notificationSender = notificationSender;
        if(notificationSender == null) LOG.warn("No notification sender configured");
    }

    public void enqueueNotifications(String msg, Set<Long> recipientAccountIds)
    {
        notificationDAO.create(msg, recipientAccountIds);
    }

    public synchronized int sendNotifications()
    {
        if(notificationSender == null) return 0;

        Set<Long> sentNotifications = Flux.fromStream
        (
            notificationDAO.findAll().stream()
                .map(notificationSender::send)
                .map(WebServiceUtil::getOnErrorLogAndSkipMono)
        )
            .flatMap(Function.identity())
            .map(Notification::getId)
            .toStream()
            .collect(Collectors.toSet());
        notificationDAO.removeByIds(sentNotifications);

        return sentNotifications.size();
    }

    public int removeExpired()
    {
        return notificationDAO.removeExpired();
    }

}
