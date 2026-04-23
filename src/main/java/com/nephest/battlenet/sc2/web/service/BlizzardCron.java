// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.Blizzard;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.util.SingleRunnable;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Blizzard
@ConditionalOnProperty(name = "com.nephest.battlenet.sc2.cron.enabled", havingValue = "true")
public class BlizzardCron
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardCron.class);

    @Autowired
    private StatsService statsService;

    @Autowired
    private StatusService statusService;

    @Autowired
    private BlizzardPrivacyService blizzardPrivacyService;

    @Autowired @Qualifier("webExecutorService")
    private ExecutorService webExecutorService;

    @Autowired @Qualifier("secondaryDbExecutorService")
    private ExecutorService secondaryDbExecutorService;

    @Autowired
    private GlobalContext globalContext;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired
    private SeasonDAO seasonDAO;

    public static final Duration MAINTENANCE_FREQUENT_FRAME = Duration.ofDays(2);
    public static final Duration MAINTENANCE_INFREQUENT_FRAME = Duration.ofDays(10);
    public static final Duration HEAVY_STATS_UPDATE_FRAME = Duration.ofDays(1);

    private TimerVar calculateHeavyStatsTask;
    private TimerVar maintenanceFrequentTask;
    private TimerVar maintenanceInfrequentTask;
    private SingleRunnable updateLaddersTask;
    private Future<Void> afterLadderUpdateTask;
    private boolean updateLadder = true;

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try
        {
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
        }
        catch(RuntimeException ex)
        {
            LOG.warn(ex.getMessage(), ex);
        }
        updateLaddersTask = new SingleRunnable(this::nonStopUpdate);
    }

    @Scheduled(fixedDelay = 10_000)
    public void updateAll()
    {
        updateLaddersTask.tryRun();
    }

    @Scheduled(cron="0 0 0/1 * * *")
    public void updateCurrentSeasonLadderStructure()
    {
        webExecutorService.submit(()->
            statsService.updateCurrentSeasonLadderStructure(globalContext.getActiveRegions()));
    }

    private void nonStopUpdate()
    {
        if(!shouldUpdate())
        {
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
