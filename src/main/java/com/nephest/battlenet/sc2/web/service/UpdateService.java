// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.service.EventService.DEFAULT_FAILURE_HANDLER;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
        for(Region region : Region.values())
        {
            //catch exceptions to allow service autowiring for tests
            try
            {
                UpdateContext updateContext = new UpdateContext
                (
                    loadLastExternalUpdate(region),
                    loadLastInternalUpdate(region)
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
            catch (RuntimeException ex)
            {
                LOG.warn(ex.getMessage(), ex);
            }
        }

        //catch exceptions to allow service autowiring for tests
        try
        {
            globalContext = new UpdateContext(loadLastExternalUpdate(null), loadLastInternalUpdate(null));
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
        catch (RuntimeException ex)
        {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    public Flux<LadderUpdate> getSaveLadderUpdateEvent()
    {
        return saveLadderUpdateEvent.asFlux();
    }

    private Instant loadLastExternalUpdate(Region region)
    {
        String updatesVar = varDAO.find((region == null ? "global" : region.getId()) + ".updated").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) return null;

        return Instant.ofEpochMilli(Long.parseLong(updatesVar));
    }

    private Instant loadLastInternalUpdate(Region region)
    {
        String updatesVar = varDAO.find((region == null ? "global" : region.getId()) + ".updated.internal").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) return null;

        return Instant.ofEpochMilli(Long.parseLong(updatesVar));
    }

    public void updated(Instant externalUpdate)
    {
        Instant internalUpdate = SC2Pulse.instant();
        varDAO.merge("global.updated", String.valueOf(externalUpdate.toEpochMilli()));
        varDAO.merge("global.updated.internal", String.valueOf(internalUpdate.toEpochMilli()));
        previousGlobalContext = globalContext;
        globalContext = new UpdateContext(externalUpdate, internalUpdate);
    }

    public void updated(Region region, Instant externalUpdate)
    {
        Instant internalUpdate = SC2Pulse.instant();
        varDAO.merge(region.getId() + ".updated", String.valueOf(externalUpdate.toEpochMilli()));
        varDAO.merge(region.getId() + ".updated.internal", String.valueOf(internalUpdate.toEpochMilli()));
        regionalContexts.put(region, new UpdateContext(externalUpdate, internalUpdate));
    }

    public UpdateContext getUpdateContext(Region region)
    {
        return region == null ? globalContext : regionalContexts.get(region);
    }

    public Duration calculateUpdateDuration(Region region)
    {
        UpdateContext context = getUpdateContext(region);
        return context == null || (region == null && (previousGlobalContext == null || globalContext.getExternalUpdate() == null))
            ? Duration.ZERO
            : Duration.between(previousGlobalContext.getExternalUpdate(), globalContext.getExternalUpdate());
    }
    
    private Set<LadderUpdate> saveLadderUpdates(LadderUpdateData data)
    {
        OffsetDateTime afterUpdate = SC2Pulse.offsetDateTime();
        if(previousLadderUpdateOdt == null)
        {
            previousLadderUpdateOdt = afterUpdate;
            return Set.of();
        }

        
        Duration duration = Duration.between(previousLadderUpdateOdt, afterUpdate);
        Set<LadderUpdate> updates = ladderUpdateDAO.create(transform(data, afterUpdate, duration));
        previousLadderUpdateOdt = afterUpdate;
        return updates;
    }
    
    private static Set<LadderUpdate> transform
    (
        LadderUpdateData data,
        OffsetDateTime created,
        Duration duration
    )
    {
        if(data.getContexts().isEmpty()) return Set.of();
        if(data.getContexts().size() > 1)
            throw new IllegalArgumentException("Multiple contexts are not supported");

        return data.getContexts().get(0).entrySet().stream()
            .flatMap(ctxEntry->ctxEntry.getValue().getData().entrySet().stream()
                .flatMap(dataEntry->dataEntry.getValue().stream()
                    .map(league->new LadderUpdate(
                        ctxEntry.getKey(),
                        dataEntry.getKey(),
                        league,
                        created,
                        duration
                    ))))
            .collect(Collectors.toSet());
    }

}
