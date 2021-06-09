// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    private Instant heavyStatsInstant;

    @Autowired
    private StatsService statsService;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired
    private ThreadPoolTaskScheduler executor;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private VarDAO varDAO;

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            varDAO.find("ladder.stats.heavy.timestamp")
                .ifPresent(timestampStr->{
                    heavyStatsInstant = Instant.ofEpochMilli(Long.parseLong(timestampStr));
                    LOG.debug("Loaded ladder heavy stats instant: {}", heavyStatsInstant);
                });
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void updateAll()
    {
        nonStopUpdate();
    }

    @Scheduled(cron="0 59 * * * *")
    public void updateSeasonState()
    {
        seasonStateDAO.merge(OffsetDateTime.now(), seasonDAO.getMaxBattlenetId());
    }

    private void nonStopUpdate()
    {
        try
        {
            doUpdateSeasons();
            calculateHeavyStats();
        }
        catch(RuntimeException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private boolean calculateHeavyStats()
    {
        if(heavyStatsInstant == null || System.currentTimeMillis() - heavyStatsInstant.toEpochMilli() >= 24 * 60 * 60 * 1000) {
            proPlayerService.update();
            queueStatsDAO.mergeCalculateForSeason(seasonDAO.getMaxBattlenetId());
            postgreSQLUtils.vacuum();
            postgreSQLUtils.analyze();
            heavyStatsInstant = Instant.ofEpochMilli(System.currentTimeMillis());
            varDAO.merge("ladder.stats.heavy.timestamp", String.valueOf(heavyStatsInstant.toEpochMilli()));
            return true;
        }
        return false;
    }

    private boolean doUpdateSeasons()
    {
        try
        {
            statsService.updateCurrent
            (
                Region.values(),
                QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new),
                BaseLeague.LeagueType.values(),
                false
            );
            matchService.update();
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
            return false;
        }
        return true;
    }

}
