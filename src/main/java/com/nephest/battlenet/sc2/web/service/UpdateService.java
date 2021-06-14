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

    private Instant lastExternalUpdate;
    private Instant lastInternalUpdate;

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
            loadLastExternalUpdate();
            loadLastInternalUpdate();
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    private void loadLastExternalUpdate()
    {
        String updatesVar = varDAO.find("global.updated").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) {
            lastExternalUpdate = null;
            return;
        }

        lastExternalUpdate = Instant.ofEpochMilli(Long.parseLong(updatesVar));
        LOG.debug("Loaded last external update: {}", lastExternalUpdate);
    }

    public void updateLastExternalUpdate(Instant instant)
    {
        lastExternalUpdate = instant;
        varDAO.merge("global.updated", String.valueOf(lastExternalUpdate.toEpochMilli()));
    }

    public Instant getLastExternalUpdate()
    {
        return lastExternalUpdate;
    }

    private void loadLastInternalUpdate()
    {
        String updatesVar = varDAO.find("global.updated.internal").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) {
            lastInternalUpdate = null;
            return;
        }

        lastInternalUpdate = Instant.ofEpochMilli(Long.parseLong(updatesVar));
        LOG.debug("Loaded last internal update: {}", lastInternalUpdate);
    }

    public void updateLastInternalUpdate(Instant instant)
    {
        lastInternalUpdate = instant;
        varDAO.merge("global.updated.internal", String.valueOf(lastInternalUpdate.toEpochMilli()));
    }

    public Instant getLastInternalUpdate()
    {
        return lastInternalUpdate;
    }

    public void updated(Instant externalUpdate)
    {
        updateLastExternalUpdate(externalUpdate);
        updateLastInternalUpdate(Instant.now());
    }

}
