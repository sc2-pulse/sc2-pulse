// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateArchiveDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class TeamStateService
{

    private static final Logger LOG = LoggerFactory.getLogger(TeamStateService.class);

    public static final int TEAM_ARCHIVE_BATCH_SIZE = 500;

    private final SeasonDAO seasonDAO;
    private final TeamDAO teamDAO;
    private final TeamStateDAO teamStateDAO;
    private final TeamStateArchiveDAO teamStateArchiveDAO;
    private final UpdateService updateService;
    private TeamStateService service;
    private int mainLengthDays, secondaryLengthDays;

    private final Map<Region, LongVar> lastArchiveSeason = new EnumMap<>(Region.class);
    private InstantVar lastClearInstant;
    private final Sinks.Many<LadderUpdateData> updateEvent = Sinks
        .many().multicast().onBackpressureBuffer(Region.values().length * 4, false);

    @Autowired
    public TeamStateService
    (
        SeasonDAO seasonDAO,
        TeamDAO teamDAO,
        TeamStateDAO teamStateDAO,
        TeamStateArchiveDAO teamStateArchiveDAO,
        VarDAO varDAO,
        EventService eventService,
        UpdateService updateService,
        @Lazy TeamStateService service,
        @Value("${com.nephest.battlenet.sc2.mmr.history.main.length:#{'180'}}") int mainLengthDays,
        @Value("${com.nephest.battlenet.sc2.mmr.history.secondary.length:#{'180'}}") int secondaryLengthDays
    )
    {
        this.seasonDAO = seasonDAO;
        this.teamDAO = teamDAO;
        this.teamStateDAO = teamStateDAO;
        this.teamStateArchiveDAO = teamStateArchiveDAO;
        this.updateService = updateService;
        this.service = service;
        this.mainLengthDays = mainLengthDays;
        this.secondaryLengthDays = secondaryLengthDays;
        initVars(varDAO);
        subToEvents(eventService);
    }

    private void initVars(VarDAO varDAO)
    {
        for(Region region : Region.values())
        {
            lastArchiveSeason.put
            (
                region,
                new LongVar
                (
                    varDAO,
                    region.getId() + ".mmr.history.archive.season",
                    false
                )
            );
        }
        lastClearInstant = new InstantVar
        (
            varDAO,
            "mmr.history.clear.update.context.timestamp",
            false
        );

        lastArchiveSeason.values().forEach(var->{
            var.tryLoad();
            if(var.getValue() == null) var.setValue(0L);
        });

        lastClearInstant.tryLoad();
        if(lastClearInstant.getValue() == null) lastClearInstant.setValue(Instant.MIN);
    }

    private void subToEvents(EventService eventService)
    {
        eventService.getLadderUpdateEvent()
            .flatMap(data->WebServiceUtil.getOnErrorLogAndSkipMono(
                Mono.fromRunnable(()->update(data)).then(Mono.just(data))), 1)
            .doOnNext(data->updateEvent.emitNext(data, EventService.DEFAULT_FAILURE_HANDLER))
            .subscribe();
    }

    protected TeamStateService getService()
    {
        return service;
    }

    protected void setService(TeamStateService service)
    {
        this.service = service;
    }

    protected void reset()
    {
        lastArchiveSeason.values().forEach(v->v.setValueAndSave(Long.MIN_VALUE));
        lastClearInstant.setValueAndSave(Instant.MIN);
    }

    protected InstantVar getLastClearInstantVar()
    {
        return lastClearInstant;
    }

    public int getMainLengthDays()
    {
        return mainLengthDays;
    }

    protected void setMainLengthDays(int mainLengthDays)
    {
        this.mainLengthDays = mainLengthDays;
    }

    public int getSecondaryLengthDays()
    {
        return secondaryLengthDays;
    }

    protected void setSecondaryLengthDays(int secondaryLengthDays)
    {
        this.secondaryLengthDays = secondaryLengthDays;
    }

    public Flux<LadderUpdateData> getUpdateEvent()
    {
        return updateEvent.asFlux();
    }

    private void update(LadderUpdateData data)
    {
        updateArchive(data);
        removeExpired();
    }

    private void updateArchive(LadderUpdateData data)
    {
        Map<Region, Set<Integer>> updates = data.getContexts().stream()
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                ()->new EnumMap<>(Region.class),
                Collectors.mapping(entry->entry.getValue().getSeason().getBattlenetId(), Collectors.toSet())
            ));
        updates.forEach((region, seasons) -> {
            //next after previous archive
            long minSeason = this.lastArchiveSeason.get(region).getValue() + 1;
            //not current season
            int maxSeason = seasonDAO.getMaxBattlenetId(region) - 1;
            seasons.stream()
                //update only previous seasons that have ended already
                .map(season->season - 1)
                .filter(season->season >= minSeason && season <= maxSeason)
                .sorted()
                .forEach(season->service.updateArchive(region, season));
        });
    }

    @Transactional
    public void updateArchive(Region region, int season)
    {
        List<Long> teamIds = teamDAO.findIds(region, season);
        if(teamIds.isEmpty())
        {
            lastArchiveSeason.get(region).setValueAndSave((long) season);
            return;
        }

        for(int i = 0; i < teamIds.size(); )
        {
            LOG.trace("Team state archive {} {} progress: {}/{}", region, season, i, teamIds.size());
            int nextIx = Math.min((i + 1) * TEAM_ARCHIVE_BATCH_SIZE, teamIds.size());
            teamStateArchiveDAO.archive(Set.copyOf(teamIds.subList(i, nextIx)));
            i = nextIx;
        }
        lastArchiveSeason.get(region).setValueAndSave((long) season);
        LOG.info("Archived team states: {} {}", region, season);
    }

    private int removeExpired()
    {
        UpdateContext ctx = updateService.getUpdateContext(null);
        if(ctx == null) return 0;

        Instant currentUpdateContext = ctx.getExternalUpdate();
        if(currentUpdateContext == null) currentUpdateContext = Instant.MIN;
        Duration offset = Duration.between(lastClearInstant.getValue(), currentUpdateContext);
        if(offset.isZero()) return 0;

        OffsetDateTime now = SC2Pulse.offsetDateTime();
        int removedMain = teamStateDAO.remove
        (
            now.minusDays(getMainLengthDays()).minus(offset),
            now.minusDays(getMainLengthDays()),
            true
        );
        if(removedMain > 0) LOG.info("Removed {} main team states", removedMain);

        int removedSecondary = teamStateDAO.remove
        (
            now.minusDays(getSecondaryLengthDays()).minus(offset),
            now.minusDays(getSecondaryLengthDays()),
            false
        );
        if(removedSecondary > 0) LOG.info("Removed {} secondary team states", removedSecondary);

        lastClearInstant.setValueAndSave(currentUpdateContext);
        return removedMain + removedSecondary;
    }

}
