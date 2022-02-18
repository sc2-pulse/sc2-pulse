// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Service
public class UpdateService
{

    private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

    private final VarDAO varDAO;

    private final Map<Region, UpdateContext> regionalContexts = new EnumMap<>(Region.class);
    private UpdateContext globalContext;

    @Autowired
    public UpdateService(VarDAO varDAO)
    {
        this.varDAO = varDAO;
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
        Instant internalUpdate = Instant.now();
        varDAO.merge("global.updated", String.valueOf(externalUpdate.toEpochMilli()));
        varDAO.merge("global.updated.internal", String.valueOf(internalUpdate.toEpochMilli()));
        globalContext = new UpdateContext(externalUpdate, internalUpdate);
    }

    public void updated(Region region, Instant externalUpdate)
    {
        Instant internalUpdate = Instant.now();
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
        return context == null
            ? Duration.ofSeconds(0)
            : Duration.between(globalContext.getExternalUpdate(), globalContext.getInternalUpdate());
    }

}
