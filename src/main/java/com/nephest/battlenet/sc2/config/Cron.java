// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.util.SingleRunnable;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.ClanService;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.GlobalContext;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.StatusService;
import com.nephest.battlenet.sc2.web.service.TwitchService;
import com.nephest.battlenet.sc2.web.service.UpdateContext;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.service.VarService;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    public static final Duration MATCH_UPDATE_FRAME = Duration.ofMinutes(50);
    public static final OffsetDateTime REPORT_UPDATE_FROM =
        OffsetDateTime.of(2021, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final Duration MAINTENANCE_FREQUENT_FRAME = Duration.ofDays(2);
    public static final Duration MAINTENANCE_INFREQUENT_FRAME = Duration.ofDays(10);
    public static final Duration MIN_UPDATE_FRAME = Duration.ofSeconds(300);
    public static final Duration MAP_STATS_DEFAULT_UPDATE_FRAME = Duration.ofMinutes(60);
    public static final Duration MAP_STATS_SKIP_NEW_SEASON_FRAME = Duration.ofDays(8);
    public static final Duration HEAVY_STATS_UPDATE_FRAME = Duration.ofDays(1);
    public static final Duration DISCORD_UPDATE_FRAME = Duration.ofDays(1);

    private TimerVar calculateHeavyStatsTask;
    private TimerVar maintenanceFrequentTask;
    private TimerVar maintenanceInfrequentTask;
    private TimerVar updateDiscordTask;
    private InstantVar matchInstant;
    private InstantVar mapStatsInstant;
    private LongVar matchUpdateFrame;
    private UpdateContext matchUpdateContext;
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
    private MatchService matchService;

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
    private MapStatsDAO mapStatsDAO;

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
    private ClanService clanService;

    @Autowired
    private TwitchService twitchService;

    @Autowired
    private DiscordService discordService;

    @Autowired
    private BlizzardPrivacyService blizzardPrivacyService;

    @Autowired
    private NotificationService notificationService;

    private SingleRunnable updateLaddersTask;

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
            matchInstant = new InstantVar(varDAO, "match.updated");
            mapStatsInstant = new InstantVar(varDAO, "ladder.stats.map.timestamp");
            matchUpdateFrame = new LongVar(varDAO, "match.update.frame", true);
            if(matchInstant.getValue() != null) matchUpdateContext = new UpdateContext(
                matchInstant.getValue().minusSeconds(MIN_UPDATE_FRAME.toSeconds()),
                matchInstant.getValue());
            if(matchUpdateFrame.getValue() == null)
                matchUpdateFrame.setValueAndSave(MATCH_UPDATE_FRAME.toMillis());
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
        updateDiscordTask.runIfAvailable();
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
            LOG.info("Can't update the ladder because request cap is reached "
                + "or updateLadder flag is set to false");
            return;
        }

        try
        {
            Instant begin = Instant.now();
            Instant lastMatchInstant = matchInstant.getValue();

            statusService.update();
            doUpdateSeasons();
            //There is a long pause here due to stats calculations in the DB, a good place to do a GC run, make a hint.
            System.gc();
            calculateHeavyStatsTask.runIfAvailable();
            clanService.update();
            blizzardPrivacyService.update();
            updateService.updated(begin);
            if(!Objects.equals(lastMatchInstant, matchInstant.getValue())) matchUpdateContext =
                updateService.getUpdateContext(null);
            updateMapStats();
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

    private void updateMapStats()
    {
        if(matchUpdateContext == null) return;
        OffsetDateTime to = OffsetDateTime.ofInstant(matchUpdateContext.getExternalUpdate(), ZoneOffset.systemDefault())
            .minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES);
        //skipping because the ladder is very volatile(top% leagues) at the beginning of the new season
        if(seasonDAO.findLast().orElseThrow().getStart().plusDays(MAP_STATS_SKIP_NEW_SEASON_FRAME.toDays()).isAfter(LocalDate.now()))
        {
            mapStatsInstant.setValueAndSave(to.toInstant());
            return;
        }

        Instant defaultInstant = mapStatsInstant.getValue() != null
            ? mapStatsInstant.getValue()
            : Instant.now().minusSeconds(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 60 + MAP_STATS_DEFAULT_UPDATE_FRAME.toSeconds());
        OffsetDateTime from = OffsetDateTime.ofInstant(defaultInstant, ZoneId.systemDefault());
        if(from.isAfter(to)) return;

        /*
            Map stats are incremental stats, preemptively update the var to prevent double calculation in exceptional
            cases. Some stats may be lost this way, but this guarantees that existing stats are 100% valid.
         */
        mapStatsInstant.setValueAndSave(to.toInstant());
        mapStatsDAO.add(from, to);
    }

    private boolean doUpdateSeasons(Region... regions)
    {
        boolean result = true;
        for(Region region : regions)
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
        return result;
    }

    private void doUpdateSeasons()
    {
        List<Future<?>> tasks = new ArrayList<>();
        for(Region region : globalContext.getActiveRegions())
            tasks.add(webExecutorService.submit(()->doUpdateSeasons(region)));

        MiscUtil.awaitAndThrowException(tasks, true, true);
        statsService.afterCurrentSeasonUpdate(false);
        try
        {
            if (shouldUpdateMatches())
            {
                UpdateContext muc = matchUpdateContext == null ? updateService.getUpdateContext(null) : matchUpdateContext;
                for(Region region : globalContext.getActiveRegions())
                    tasks.add(webExecutorService.submit(()->matchService.update(muc, region)));
                MiscUtil.awaitAndThrowException(tasks, true, true);
                matchService.updateMeta(muc);
                matchInstant.setValueAndSave(Instant.now());
                twitchService.update();
            }
        }
        catch (RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
    }

    private boolean shouldUpdateMatches()
    {
        return matchInstant.getValue() == null
            || System.currentTimeMillis() - matchInstant.getValue().toEpochMilli() >= getMatchUpdateFrame().toMillis();
    }

    private boolean shouldUpdate()
    {
        return updateLadder && sc2API.requestCapNotReached();
    }

    public void setShouldUpdateLadder(boolean updateLadder)
    {
        this.updateLadder = updateLadder;
    }

    public Duration getMatchUpdateFrame()
    {
        return Duration.ofMillis(matchUpdateFrame.getValue());
    }

    public void setMatchUpdateFrame(@NonNull Duration matchUpdateFrame)
    {
        this.matchUpdateFrame.setValueAndSave(matchUpdateFrame.toMillis());
        LOG.info("Match update frame: {}", matchUpdateFrame);
    }

    private void commenceMaintenance()
    {
        maintenanceFrequentTask.runIfAvailable();
        maintenanceInfrequentTask.runIfAvailable();
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
