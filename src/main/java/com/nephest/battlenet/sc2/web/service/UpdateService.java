// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;

@Service
public class UpdateService
{

    private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

    private final VarDAO varDAO;

    private UpdateContext updateContext;

    @Autowired
    public UpdateService(VarDAO varDAO)
    {
        this.varDAO = varDAO;
    }

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            updateContext = new UpdateContext(
                loadLastExternalUpdate(),
                loadLastInternalUpdate()
            );
            LOG.debug("Loaded last update context: {} {}", updateContext.getExternalUpdate(), updateContext.getInternalUpdate());
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    private Instant loadLastExternalUpdate()
    {
        String updatesVar = varDAO.find("global.updated").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) return null;

        return Instant.ofEpochMilli(Long.parseLong(updatesVar));
    }

    private Instant loadLastInternalUpdate()
    {
        String updatesVar = varDAO.find("global.updated.internal").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) return null;

        return Instant.ofEpochMilli(Long.parseLong(updatesVar));
    }

    public void updated(Instant externalUpdate)
    {
        Instant internalUpdate = Instant.now();
        varDAO.merge("global.updated", String.valueOf(externalUpdate.toEpochMilli()));
        varDAO.merge("global.updated.internal", String.valueOf(internalUpdate.toEpochMilli()));
        updateContext = new UpdateContext(externalUpdate, internalUpdate);
    }

    public UpdateContext getUpdateContext()
    {
        return updateContext;
    }

}
