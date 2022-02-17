// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class APIHealthMonitor
{

    private static final Logger LOG = LoggerFactory.getLogger(APIHealthMonitor.class);

    private final AtomicLong requests = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final LongVar requestsVar;
    private final LongVar errorsVar;
    private double errorRate;

    public APIHealthMonitor(VarDAO varDAO, String prefix)
    {
        requestsVar = new LongVar(varDAO, prefix + ".request.count", false);
        errorsVar = new LongVar(varDAO, prefix + ".error.count", false);
        //catch exceptions so that tests could be run in any environment, even without vars
        try
        {
            Long requestsLoaded = requestsVar.load();
            Long errorsLoaded = errorsVar.load();
            if(requestsLoaded != null && errorsLoaded != null)
            {
                requests.set(requestsLoaded);
                errors.set(errorsLoaded);
                errorRate = requestsLoaded == 0
                    ? 0.0
                    : (errorsLoaded / (double) requestsLoaded) * 100;
            }
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
        errorRate = requestCount == 0
            ? 0.0
            : (errorCount / (double) requestCount) * 100;
        save();
        return errorRate;
    }

    public long getRequests()
    {
        return requests.get();
    }

    public long getErrors()
    {
        return errors.get();
    }

    public double getErrorRate()
    {
        return errorRate;
    }

    public double getHealth()
    {
        return 100 - getErrorRate();
    }

    public void save()
    {
        requestsVar.setValueAndSave(requests.get());
        errorsVar.setValueAndSave(errors.get());
    }

}
