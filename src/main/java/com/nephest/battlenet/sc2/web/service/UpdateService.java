// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.service.EventService.DEFAULT_FAILURE_HANDLER;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import com.nephest.battlenet.sc2.model.local.Var;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class UpdateService
{

    private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

    private final VarDAO varDAO;
    private final LadderUpdateDAO ladderUpdateDAO;

    private final Map<Region, UpdateContext> regionalContexts = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> externalUpdates = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> internalUpdates = new EnumMap<>(Region.class);
    private InstantVar globalExternalUpdate;
    private InstantVar globalInternalUpdate;
    private final Sinks.Many<LadderUpdate> saveLadderUpdateEvent = Sinks.unsafe()
        .many().multicast().onBackpressureBuffer(280);
    private UpdateContext globalContext;
    private UpdateContext previousGlobalContext;
    private OffsetDateTime previousLadderUpdateOdt;

    @Autowired
    public UpdateService
    (
        VarDAO varDAO,
        EventService eventService,
        LadderUpdateDAO ladderUpdateDAO
    )
    {
        this.varDAO = varDAO;
        this.ladderUpdateDAO = ladderUpdateDAO;
        subToEvents(eventService);
    }

    private void subToEvents(EventService eventService)
    {
        eventService.getLadderUpdateEvent()
            .flatMap(data->WebServiceUtil.getOnErrorLogAndSkipMono(Mono.fromCallable(()->
                saveLadderUpdates(data))))
            .doOnNext(updates->updates.forEach(
                u->saveLadderUpdateEvent.emitNext(u, DEFAULT_FAILURE_HANDLER)))
            .subscribe();
    }

    @PostConstruct
    public void init()
    {
        globalExternalUpdate = new InstantVar(varDAO, "global.updated", false);
        globalInternalUpdate = new InstantVar(varDAO, "global.updated.internal", false);
        for(Region region : Region.values())
        {
            externalUpdates.put(region, new InstantVar(varDAO, region.getId() + ".updated", false));
            internalUpdates.put(region, new InstantVar(varDAO, region.getId() + ".updated.internal", false));
        }
        Stream.of
        (
            List.of(globalExternalUpdate, globalInternalUpdate),
            externalUpdates.values(),
            internalUpdates.values()
        )
            .flatMap(Collection::stream)
            .forEach(Var::tryLoad);

        for(Region region : Region.values())
        {
            UpdateContext updateContext = new UpdateContext
            (
                externalUpdates.get(region).getValue(),
                internalUpdates.get(region).getValue()
            );
            LOG.debug
            (
                "Loaded last update context: {} {} {}",
                region,
                updateContext.getExternalUpdate(),
                updateContext.getInternalUpdate()
            );
            regionalContexts.put(region, updateContext);
        }

        globalContext = new UpdateContext(globalExternalUpdate.getValue(), globalInternalUpdate.getValue());
        previousGlobalContext = new UpdateContext(globalContext.getExternalUpdate(), globalContext.getInternalUpdate());
        if(globalContext.getInternalUpdate() != null) previousLadderUpdateOdt =
            globalContext.getInternalUpdate().atOffset(SC2Pulse.offsetDateTime().getOffset());
        LOG.debug
        (
            "Loaded last update context: {} {}",
            globalContext.getExternalUpdate(),
            globalContext.getInternalUpdate()
        );
    }

    @Scheduled(cron="0 0 6 * * *")
    public int removeExpiredLadderUpdates()
    {
        return ladderUpdateDAO.removeExpired();
    }

    public OffsetDateTime getPreviousLadderUpdateOffsetDateTime()
    {
        return previousLadderUpdateOdt;
    }

    protected void setPreviousLadderUpdateOffsetDateTime(OffsetDateTime previousLadderUpdateOdt)
    {
        this.previousLadderUpdateOdt = previousLadderUpdateOdt;
    }

    public Flux<LadderUpdate> getSaveLadderUpdateEvent()
    {
        return saveLadderUpdateEvent.asFlux();
    }

    public void updated(Instant externalUpdate)
    {
        Instant internalUpdate = SC2Pulse.instant();
        globalExternalUpdate.setValueAndSave(externalUpdate);
        globalInternalUpdate.setValueAndSave(internalUpdate);
        previousGlobalContext = globalContext;
        globalContext = new UpdateContext(externalUpdate, internalUpdate);
    }

    public void updated(Region region, Instant externalUpdate)
    {
        Instant internalUpdate = SC2Pulse.instant();
        externalUpdates.get(region).setValueAndSave(externalUpdate);
        internalUpdates.get(region).setValueAndSave(internalUpdate);
        regionalContexts.put(region, new UpdateContext(externalUpdate, internalUpdate));
    }

    public UpdateContext getUpdateContext(Region region)
    {
        return region == null ? globalContext : regionalContexts.get(region);
    }

    public Duration calculateUpdateDuration(Region region)
    {
        UpdateContext context = getUpdateContext(region);
        return context == null
            || (region == null
                && (previousGlobalContext.getExternalUpdate() == null || globalContext.getExternalUpdate() == null))
            ? Duration.ZERO
            : Duration.between(previousGlobalContext.getExternalUpdate(), globalContext.getExternalUpdate());
    }
    
    protected Set<LadderUpdate> saveLadderUpdates(LadderUpdateData data)
    {
        OffsetDateTime afterUpdate = SC2Pulse.offsetDateTime();
        if(previousLadderUpdateOdt == null)
        {
            previousLadderUpdateOdt = afterUpdate;
            return Set.of();
        }

        
        Duration duration = Duration.between(previousLadderUpdateOdt, afterUpdate);
        Set<LadderUpdate> updates = ladderUpdateDAO
            .create(DAOUtils.toCollisionFreeSet(transform(data, afterUpdate, duration)));
        previousLadderUpdateOdt = afterUpdate;
        return updates;
    }
    
    private static List<LadderUpdate> transform
    (
        LadderUpdateData data,
        OffsetDateTime created,
        Duration duration
    )
    {
        if(data.getContexts().isEmpty()) return List.of();

        return data.getContexts().stream()
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .flatMap(ctxEntry->ctxEntry.getValue().getData().entrySet().stream()
                .flatMap(dataEntry->dataEntry.getValue().stream()
                    .map(league->new LadderUpdate(
                        ctxEntry.getKey(),
                        dataEntry.getKey(),
                        league,
                        created,
                        duration
                    ))))
            .toList();
    }

}
