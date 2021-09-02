// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.*;
import java.util.Objects;

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    public static final Duration MATCH_UPDATE_FRAME = Duration.ofMinutes(50);
    public static final OffsetDateTime REPORT_UPDATE_FROM =
        OffsetDateTime.of(2021, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);

    private Instant heavyStatsInstant;
    private Instant matchInstant;
    private UpdateContext matchUpdateContext;

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
    private TeamStateDAO teamStateDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired
    private ThreadPoolTaskScheduler executor;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private PlayerCharacterReportService characterReportService;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private VarService varService;

    @Autowired
    private UpdateService updateService;

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

    public static OffsetDateTime getNextCharacterReportUpdateTime()
    {
        OffsetDateTime dt = OffsetDateTime.now().withHour(5).withMinute(0).withSecond(0).withNano(0);
        if(dt.compareTo(OffsetDateTime.now()) < 0) dt = dt.plusDays(1);
        return dt;
    }

    @Scheduled(fixedDelay = 30_000)
    public void updateAll()
    {
        nonStopUpdate();
    }

    @Scheduled(cron="0 0 5 * * *")
    public void updateCharacterReports()
    {
        characterReportService.update(REPORT_UPDATE_FROM);
    }

    @Scheduled(cron="0 0 0/1 * * *")
    public void evictVarCache()
    {
        varService.evictCache();
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
            Instant begin = Instant.now();
            Instant lastMatchInstant = matchInstant;

            doUpdateSeasons();
            statsService.afterCurrentSeasonUpdate(updateService.getUpdateContext(null), false);
            calculateHeavyStats();
            updateService.updated(begin);
            if(!Objects.equals(lastMatchInstant, matchInstant)) matchUpdateContext = updateService.getUpdateContext(null);
        }
        catch(RuntimeException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private boolean calculateHeavyStats()
    {
        if(heavyStatsInstant == null || System.currentTimeMillis() - heavyStatsInstant.toEpochMilli() >= 24 * 60 * 60 * 1000) {
            Instant defaultInstant = heavyStatsInstant != null
                ? heavyStatsInstant
                : Instant.now().minusSeconds(24 * 60 * 60 * 1000);
            OffsetDateTime defaultOdt = OffsetDateTime.ofInstant(defaultInstant, ZoneId.systemDefault());
            proPlayerService.update();
            queueStatsDAO.mergeCalculateForSeason(seasonDAO.getMaxBattlenetId());
            teamStateDAO.archive(defaultOdt);
            teamStateDAO.cleanArchive(defaultOdt);
            teamStateDAO.removeExpired();
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
        boolean result = true;
        for(Region region : Region.values())
        {
            try
            {
                Instant begin = Instant.now();
                statsService.updateCurrent
                (
                    new Region[]{region},
                    QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new),
                    BaseLeague.LeagueType.values(),
                    false,
                    updateService.getUpdateContext(region)
                );
                updateService.updated(region, begin);
            }
            catch (RuntimeException ex)
            {
                //API can be broken randomly. All we can do at this point is log the exception.
                LOG.error(ex.getMessage(), ex);
                result = false;
            }
        }

        try
        {
            if (shouldUpdateMatches())
            {
                matchService.update(matchUpdateContext == null ? updateService.getUpdateContext(null) : matchUpdateContext);
                matchInstant = Instant.now();
            }
        }
        catch (RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
            result = false;
        }
        return result;
    }

    private boolean shouldUpdateMatches()
    {
        return matchInstant == null
            || System.currentTimeMillis() - matchInstant.toEpochMilli() >= MATCH_UPDATE_FRAME.toMillis();
    }

}
