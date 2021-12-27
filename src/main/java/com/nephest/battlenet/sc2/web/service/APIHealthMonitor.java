// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.DoubleVar;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class APIHealthMonitor
{

    private static final Logger LOG = LoggerFactory.getLogger(APIHealthMonitor.class);

    private final AtomicLong requests = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final DoubleVar errorRate;

    public APIHealthMonitor(VarDAO varDAO, String prefix)
    {
        errorRate = new DoubleVar(varDAO, prefix + ".error.rate", false);
        //catch exceptions so that tests could be run in any environment, even without vars
        try
        {
            errorRate.load();
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public void addRequest()
    {
        requests.incrementAndGet();
    }

    public void addError()
    {
        errors.incrementAndGet();
    }

    public Double update()
    {
        long requestCount = requests.getAndSet(0);
        long errorCount = errors.getAndSet(0);
        double errorRate = requestCount == 0
            ? 0.0
            : (errorCount / (double) requestCount) * 100;
        this.errorRate.setValueAndSave(errorRate);
        return this.errorRate.getValue();
    }

    public long getRequests()
    {
        return requests.get();
    }

    public long getErrors()
    {
        return errors.get();
    }

    public Double getErrorRate()
    {
        return errorRate.getValue() == null ? 0 : errorRate.getValue();
    }

    public Double getHealth()
    {
        return 100 - getErrorRate();
    }

}
