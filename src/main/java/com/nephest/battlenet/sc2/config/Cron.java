// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PersistentLoginDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.ClanService;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.StatusService;
import com.nephest.battlenet.sc2.web.service.TwitchService;
import com.nephest.battlenet.sc2.web.service.UpdateContext;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.service.VarService;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static final Duration MATCH_UPDATE_FRAME = Duration.ofMinutes(50);
    public static final OffsetDateTime REPORT_UPDATE_FROM =
        OffsetDateTime.of(2021, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final Duration MAINTENANCE_FREQUENT_FRAME = Duration.ofDays(2);
    public static final Duration MAINTENANCE_INFREQUENT_FRAME = Duration.ofDays(10);
    public static final Duration MIN_UPDATE_FRAME = Duration.ofSeconds(300);
    public static final Duration MAP_STATS_DEFAULT_UPDATE_FRAME = Duration.ofMinutes(60);
    public static final Duration MAP_STATS_SKIP_NEW_SEASON_FRAME = Duration.ofDays(8);

    private InstantVar heavyStatsInstant;
    private InstantVar maintenanceFrequentInstant;
    private InstantVar maintenanceInfrequentInstant;
    private InstantVar matchInstant;
    private InstantVar mapStatsInstant;
    private UpdateContext matchUpdateContext;

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
    private PersistentLoginDAO persistentLoginDAO;

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
    private BlizzardPrivacyService blizzardPrivacyService;

    private final AtomicBoolean updatingLadders = new AtomicBoolean(false);

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            heavyStatsInstant = new InstantVar(varDAO, "ladder.stats.heavy.timestamp");
            maintenanceFrequentInstant = new InstantVar(varDAO, "maintenance.frequent");
            maintenanceInfrequentInstant = new InstantVar(varDAO, "maintenance.infrequent");
            matchInstant = new InstantVar(varDAO, "match.updated");
            mapStatsInstant = new InstantVar(varDAO, "ladder.stats.map.timestamp");
            if(matchInstant.getValue() != null) matchUpdateContext = new UpdateContext(
                matchInstant.getValue().minusSeconds(MIN_UPDATE_FRAME.toSeconds()),
                matchInstant.getValue());
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

    @Scheduled(fixedDelay = 10_000)
    public void updateAll()
    {
        if(!updatingLadders.compareAndSet(false, true)) return;

        nonStopUpdate();
        updatingLadders.set(false);
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
        if(!shouldUpdate()) {
            LOG.info("Can't update the ladder because request cap is reached");
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
            statsService.afterCurrentSeasonUpdate(updateService.getUpdateContext(null), false);
            calculateHeavyStats();
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

    private boolean calculateHeavyStats()
    {
        if(heavyStatsInstant.getValue() == null || System.currentTimeMillis() - heavyStatsInstant.getValue().toEpochMilli() >= 24 * 60 * 60 * 1000) {
            Instant defaultInstant = heavyStatsInstant.getValue() != null
                ? heavyStatsInstant.getValue()
                : Instant.now().minusSeconds(24 * 60 * 60 * 1000);
            OffsetDateTime defaultOdt = OffsetDateTime.ofInstant(defaultInstant, ZoneId.systemDefault());
            proPlayerService.update();
            for(Integer season : seasonDAO.getLastInAllRegions())
                queueStatsDAO.mergeCalculateForSeason(season);
            teamStateDAO.archive(defaultOdt);
            teamStateDAO.cleanArchive(defaultOdt);
            teamStateDAO.removeExpired();
            evidenceDAO.nullifyReporterIps(defaultOdt);
            heavyStatsInstant.setValueAndSave(Instant.ofEpochMilli(System.currentTimeMillis()));
            return true;
        }
        return false;
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
        for(Region region : Region.values()) tasks.add(webExecutorService.submit(()->doUpdateSeasons(region)));

        MiscUtil.awaitAndThrowException(tasks, true, true);

        try
        {
            if (shouldUpdateMatches())
            {
                UpdateContext muc = matchUpdateContext == null ? updateService.getUpdateContext(null) : matchUpdateContext;
                for(Region region : Region.values())
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
            || System.currentTimeMillis() - matchInstant.getValue().toEpochMilli() >= MATCH_UPDATE_FRAME.toMillis();
    }

    private boolean shouldUpdate()
    {
        return sc2API.requestCapNotReached();
    }

    private void commenceMaintenance()
    {
        if
        (
            maintenanceFrequentInstant.getValue() == null
            || System.currentTimeMillis() - maintenanceFrequentInstant.getValue().toEpochMilli() >= MAINTENANCE_FREQUENT_FRAME.toMillis()
        )
            commenceFrequentMaintenance();
        if
        (
            maintenanceInfrequentInstant.getValue() == null
            || System.currentTimeMillis() - maintenanceInfrequentInstant.getValue().toEpochMilli() >= MAINTENANCE_INFREQUENT_FRAME.toMillis()
        )
            commenceInfrequentMaintenance();
    }

    private void commenceFrequentMaintenance()
    {
        postgreSQLUtils.reindex("ix_match_updated");
        this.maintenanceFrequentInstant.setValueAndSave(Instant.now());
    }

    private void commenceInfrequentMaintenance()
    {
        postgreSQLUtils.reindex
        (
            "ix_team_state_team_id_archived",
            "ix_team_state_timestamp",
            "ix_account_updated",
            "ix_player_character_updated"
        );
        persistentLoginDAO.removeExpired();
        this.maintenanceInfrequentInstant.setValueAndSave(Instant.now());
    }

}
