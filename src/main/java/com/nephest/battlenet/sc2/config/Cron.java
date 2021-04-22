// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    private static final BaseLeague.LeagueType[] NORMAL_LEAGUES = BaseLeague.LeagueType.values();
    private static final BaseLeague.LeagueType[] ALTERNATIVE_LEAGUES = new BaseLeague.LeagueType[]
    {
        BaseLeague.LeagueType.GRANDMASTER,
        BaseLeague.LeagueType.MASTER,
        BaseLeague.LeagueType.DIAMOND
    };

    @Autowired
    private StatsService statsService;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired
    private ThreadPoolTaskScheduler executor;

    @Scheduled(cron="0 0 7 * * *")
    public void updateAll()
    {
        proPlayerService.update();
        matchService.update();
        postgreSQLUtils.vacuum();
        postgreSQLUtils.analyze();
        updateSeasonsHourCycle();
    }

    @Scheduled(cron="0 0 0-6,8-23 * * MON-TUE,THU-SUN")
    public void updateSeasons()
    {
        updateSeasonsHourCycle();
    }

    @Scheduled(cron="0 0 0-6,8-11,13-23 * * WED")
    public void updateSeasonsWed()
    {
        updateSeasonsHourCycle();
    }

    @Scheduled(cron="0 10 12 * * WED")
    public void updateSeasonsPreMaintenance()
    {
        updateSeasonsHourCycle();
    }

    private void updateSeasonsHourCycle()
    {
        Instant startInstant = Instant.now();
        int cycleMinutes = 60 - OffsetDateTime.now().getMinute();
        long start = System.currentTimeMillis();
        doUpdateSeasons();
        // + 2.5 minutes just to be safe
        long taskDurationMinutes = (long) ((System.currentTimeMillis() - start) + 60000 * 2.5) / 1000 / 60;
        long scans = Math.min(cycleMinutes / taskDurationMinutes, 4);
        long minutesBetweenTasks = 60 / scans;

        for(int i = 1; i < scans; i++) {
            Instant nextInstant = startInstant.plus(minutesBetweenTasks * i, ChronoUnit.MINUTES);
            executor.schedule(this::doUpdateSeasons, nextInstant);
            LOG.debug("Scheduled seasons update at {}", nextInstant);
        }
        if(scans < 2 && OffsetDateTime.now().getMinute() < 42)  {
            Instant nextInstant = OffsetDateTime.now().withMinute(42).withSecond(0).toInstant();
            executor.schedule(this::doUpdateTop, nextInstant);
            LOG.debug("Scheduled top update at {}", nextInstant);
        }
    }

    private void doUpdateSeasons()
    {
        try
        {
            statsService.updateCurrent
            (
                Region.values(),
                QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new),
                BaseLeague.LeagueType.values()
            );
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void doUpdateTop()
    {
        try
        {
            statsService.updateCurrent
            (
                new Region[]{Region.US, Region.EU, Region.KR},
                new QueueType[]{QueueType.LOTV_1V1},
                statsService.getAlternativeRegions().contains(Region.EU)
                    || statsService.getAlternativeRegions().contains(Region.US)
                        ? ALTERNATIVE_LEAGUES
                        : NORMAL_LEAGUES
            );
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
    }

}
