// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.util.SingleRunnable;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.GlobalContext;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.StatusService;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.service.VarService;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    public static final OffsetDateTime REPORT_UPDATE_FROM =
        OffsetDateTime.of(2021, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final Duration MAINTENANCE_FREQUENT_FRAME = Duration.ofDays(2);
    public static final Duration MAINTENANCE_INFREQUENT_FRAME = Duration.ofDays(10);
    public static final Duration HEAVY_STATS_UPDATE_FRAME = Duration.ofDays(1);
    public static final Duration DISCORD_UPDATE_FRAME = Duration.ofDays(1);

    private TimerVar calculateHeavyStatsTask;
    private TimerVar maintenanceFrequentTask;
    private TimerVar maintenanceInfrequentTask;
    private TimerVar updateDiscordTask;
    private boolean updateLadder = true;

    @Autowired
    private GlobalContext globalContext;

    @Autowired
    private BlizzardSC2API sc2API;

    @Autowired
    private StatsService statsService;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired @Qualifier("webExecutorService")
    private ExecutorService webExecutorService;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private PlayerCharacterReportService characterReportService;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private VarService varService;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private StatusService statusService;

    @Autowired
    private DiscordService discordService;

    @Autowired
    private BlizzardPrivacyService blizzardPrivacyService;

    @Autowired
    private NotificationService notificationService;

    private SingleRunnable updateLaddersTask;
    private Future<?> afterLadderUpdateTask;

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            calculateHeavyStatsTask = new TimerVar
            (
                varDAO,
                "ladder.stats.heavy.timestamp",
                true,
                HEAVY_STATS_UPDATE_FRAME,
                this::calculateHeavyStats
            );
            maintenanceFrequentTask = new TimerVar
            (
                varDAO,
                "maintenance.frequent",
                true,
                MAINTENANCE_FREQUENT_FRAME,
                this::commenceFrequentMaintenance
            );
            maintenanceInfrequentTask = new TimerVar
            (
                varDAO,
                "maintenance.infrequent",
                true,
                MAINTENANCE_INFREQUENT_FRAME,
                this::commenceInfrequentMaintenance
            );
            updateDiscordTask = new TimerVar
            (
                varDAO,
                "discord.update.timestamp",
                true,
                DISCORD_UPDATE_FRAME,
                ()->webExecutorService.submit(discordService::update)
            );
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
        updateLaddersTask = new SingleRunnable(this::nonStopUpdate);
    }

    public static OffsetDateTime getNextCharacterReportUpdateTime()
    {
        OffsetDateTime dt = OffsetDateTime.now().withHour(5).withMinute(0).withSecond(0).withNano(0);
        if(dt.isBefore(OffsetDateTime.now())) dt = dt.plusDays(1);
        return dt;
    }

    @Scheduled(fixedDelay = 10_000)
    public void updateAll()
    {
        updateLaddersTask.tryRun();
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
        seasonStateDAO.merge(OffsetDateTime.now(), seasonDAO.getMaxBattlenetId());
    }

    @Scheduled(cron="0 0/10 * * * *")
    public void updateBackgroundServices()
    {
        updateDiscordTask.runIfAvailable().block();
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

    private void nonStopUpdate()
    {
        if(!shouldUpdate()) {
            LOG.info("Can't update the ladder because updateLadder flag is set to false");
            return;
        }

        try
        {
            Instant begin = Instant.now();

            statusService.update();
            doUpdateSeasons();
            //There is a long pause here due to stats calculations in the DB, a good place to do a GC run, make a hint.
            System.gc();
            calculateHeavyStatsTask.runIfAvailable().block();
            blizzardPrivacyService.update();
            updateService.updated(begin);
            commenceMaintenance();
            LOG.info("Update cycle completed. Duration: {} seconds", (System.currentTimeMillis() - begin.toEpochMilli()) / 1000);
        }
        catch(RuntimeException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void calculateHeavyStats()
    {
        Instant defaultInstant = calculateHeavyStatsTask.getValue() != null
            ? calculateHeavyStatsTask.getValue()
            : Instant.now().minusSeconds(24 * 60 * 60 * 1000);
        OffsetDateTime defaultOdt = OffsetDateTime.ofInstant(defaultInstant, ZoneId.systemDefault());
        for(Integer season : seasonDAO.getLastInAllRegions())
            queueStatsDAO.mergeCalculateForSeason(season);
        teamStateDAO.archive(defaultOdt);
        teamStateDAO.cleanArchive(defaultOdt);
        teamStateDAO.removeExpired();
        evidenceDAO.nullifyReporterIps(defaultOdt);
    }

    private boolean doUpdateSeasons(Region... regions)
    {
        boolean result = true;
        try
        {
            Instant begin = Instant.now();
            statsService.updateCurrent
            (
                regions,
                QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new),
                BaseLeague.LeagueType.values(),
                false,
                updateService.getUpdateContext(null)
            );
            for(Region region : regions) updateService.updated(region, begin);
        }
        catch (RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
            result = false;
        }
        return result;
    }

    private void doUpdateSeasons()
    {
        doUpdateSeasons(globalContext.getActiveRegions().toArray(Region[]::new));
        if(afterLadderUpdateTask != null)
        {
            try
            {
                if(!afterLadderUpdateTask.isDone())
                    LOG.warn("Waiting for previous post ladder update task to complete");
                afterLadderUpdateTask.get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                throw new RuntimeException(e);
            }
        }
        afterLadderUpdateTask = statsService.afterCurrentSeasonUpdate(false);
    }

    private boolean shouldUpdate()
    {
        return updateLadder;
    }

    public void setShouldUpdateLadder(boolean updateLadder)
    {
        this.updateLadder = updateLadder;
    }

    private void commenceMaintenance()
    {
        maintenanceFrequentTask.runIfAvailable().block();
        maintenanceInfrequentTask.runIfAvailable().block();
    }

    private void commenceFrequentMaintenance()
    {
        postgreSQLUtils.reindex("ix_match_updated");
    }

    private void commenceInfrequentMaintenance()
    {
        postgreSQLUtils.reindex
        (
            "ix_team_state_team_id_archived",
            "uq_match_date_type_map_id_region",
            "match_pkey",
            "ix_match_participant_team_id_team_state_timestamp",
            "match_participant_pkey",
            "ix_account_updated",
            "ix_player_character_updated",
            "ix_clan_member_updated"
        );
    }

}
