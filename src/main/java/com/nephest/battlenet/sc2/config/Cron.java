// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.VarService;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "com.nephest.battlenet.sc2.cron.enabled", havingValue = "true")
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    public static final OffsetDateTime REPORT_UPDATE_FROM =
        OffsetDateTime.of(2021, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired @Qualifier("webExecutorService")
    private ExecutorService webExecutorService;

    @Autowired @Qualifier("secondaryDbExecutorService")
    private ExecutorService secondaryDbExecutorService;

    @Autowired
    private PlayerCharacterReportService characterReportService;

    @Autowired
    private VarService varService;

    @Autowired
    private NotificationService notificationService;

    public static OffsetDateTime getNextCharacterReportUpdateTime()
    {
        OffsetDateTime dt = SC2Pulse.offsetDateTime().withHour(5).withMinute(0).withSecond(0).withNano(0);
        if(dt.isBefore(SC2Pulse.offsetDateTime())) dt = dt.plusDays(1);
        return dt;
    }

    @Scheduled(cron="0 0 5 * * *")
    public void updateCharacterReports()
    {
        characterReportService.update(REPORT_UPDATE_FROM);
    }

    @Scheduled(cron="0 0 4 * * *")
    public void updateProPlayers()
    {
        proPlayerService.update().subscribe();
    }

    @Scheduled(cron="0 0 0/1 * * *")
    public void evictVarCache()
    {
        varService.evictCache();
    }

    @Scheduled(cron="0 59 * * * *")
    public void updateSeasonState()
    {
        seasonStateDAO.merge(SC2Pulse.offsetDateTime(), seasonDAO.getMaxBattlenetId());
    }

    @Scheduled(cron="0 0/10 * * * *")
    public void sendNotifications()
    {
        webExecutorService.submit(()->{
            int notificationsRemoved = notificationService.removeExpired();
            if(notificationsRemoved > 0) LOG.info("Removed {} expired notifications", notificationsRemoved);

            int notificationsSent = notificationService.sendNotifications();
            if(notificationsSent > 0) LOG.info("Sent {} notifications", notificationsSent);
        });
    }

}
