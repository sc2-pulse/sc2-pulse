// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.util.SingleRunnable;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.GlobalContext;
import com.nephest.battlenet.sc2.web.service.LadderUpdateContext;
import com.nephest.battlenet.sc2.web.service.LadderUpdateTaskContext;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.StatusService;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.service.VarService;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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

    @Autowired @Qualifier("secondaryDbExecutorService")
    private ExecutorService secondaryDbExecutorService;

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
    private Future<Void> afterLadderUpdateTask;

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
        OffsetDateTime dt = SC2Pulse.offsetDateTime().withHour(5).withMinute(0).withSecond(0).withNano(0);
        if(dt.isBefore(SC2Pulse.offsetDateTime())) dt = dt.plusDays(1);
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
        seasonStateDAO.merge(SC2Pulse.offsetDateTime(), seasonDAO.getMaxBattlenetId());
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
            Instant begin = SC2Pulse.instant();

            statusService.update();
            doUpdateSeasons();
            //There is a long pause here due to stats calculations in the DB, a good place to do a GC run, make a hint.
            System.gc();
            calculateHeavyStatsTask.runIfAvailable()
                .subscribe(run->{if(run) LOG.info("Updated heavy stats");});
            blizzardPrivacyService.update();
            updateService.updated(begin);
            commenceMaintenance();
            LOG.info("Update cycle completed. Duration: {}", Duration.between(begin, SC2Pulse.instant()));
        }
        catch(RuntimeException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void calculateHeavyStats()
    {
        Instant defaultInstant = calculateHeavyStatsTask.getValue() != null
            ? calculateHeavyStatsTask.getValue()
            : SC2Pulse.instant().minusSeconds(24 * 60 * 60 * 1000);
        OffsetDateTime defaultOdt = OffsetDateTime.ofInstant(defaultInstant, ZoneId.systemDefault());
        for(Integer season : seasonDAO.getLastInAllRegions())
            queueStatsDAO.mergeCalculateForSeason(season);
        evidenceDAO.nullifyReporterIps(defaultOdt);
    }

    private boolean doUpdateSeasons(Region... regions)
    {
        boolean result = true;
        Instant begin = SC2Pulse.instant();
        for(Region region : regions)
        {
            try
            {
                MiscUtil.awaitAndLogExceptions
                (
                    statsService.updateCurrent
                    (
                        Map.of(region, LadderUpdateContext.ALL),
                        false
                    ).values().stream()
                        .map(LadderUpdateTaskContext::getTasks)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()),
                    true
                );
            }
            catch (RuntimeException ex)
            {
                //API can be broken randomly. All we can do at this point is log the exception.
                LOG.error(ex.getMessage(), ex);
                result = false;
            }
        }
        for(Region region : regions) updateService.updated(region, begin);
        return result;
    }

    private void doUpdateSeasons()
    {
        List<Future<Void>> tasks = new ArrayList<>();
        for(Region region : globalContext.getActiveRegions())
            tasks.add(webExecutorService.submit(()->doUpdateSeasons(region), null));

        MiscUtil.awaitAndThrowException(tasks, true, true);
        if(afterLadderUpdateTask != null && !afterLadderUpdateTask.isDone())
        {
            LOG.warn("Waiting for previous post ladder update task to complete");
            try
            {
                afterLadderUpdateTask.get();
            }
            catch (ExecutionException e)
            {
                afterLadderUpdateTask = statsService.afterCurrentSeasonUpdate(false);
                throw new RuntimeException(e);
            }
            catch (InterruptedException e)
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
        secondaryDbExecutorService.submit(()->
            postgreSQLUtils.reindex(Set.of("ix_match_updated"), true)
        );
    }

    private void commenceInfrequentMaintenance()
    {
        secondaryDbExecutorService.submit(()->postgreSQLUtils.reindex(Set.of(
            "uq_match_date_type_map_id_region",
            "match_pkey",
            "ix_match_participant_team_id_team_state_timestamp",
            "match_participant_pkey",
            "ix_account_updated",
            "ix_player_character_updated",
            "ix_clan_member_updated",
            "ix_recent_team_search",

            "ix_clan_search_active_members",
            "ix_clan_search_avg_rating",
            "ix_clan_search_games",
            "ix_clan_search_members"
        ), true));
    }

}
