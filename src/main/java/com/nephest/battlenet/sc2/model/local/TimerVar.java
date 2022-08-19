// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This var encapsulates common logic for simple timer based tasks(run every x duration).
 */
public class TimerVar
extends InstantVar
{

    private static final Logger LOG = LoggerFactory.getLogger(TimerVar.class);

    private final Duration durationBetweenRuns;
    private final Runnable task;

    public TimerVar
    (
        VarDAO varDAO,
        String key,
        boolean load,
        Duration durationBetweenRuns,
        Runnable task
    )
    {
        super(varDAO, key, load);
        this.durationBetweenRuns = durationBetweenRuns;
        this.task = task;
    }

    public Duration getDurationBetweenRuns()
    {
        return durationBetweenRuns;
    }

    public Instant availableOn()
    {
        return getValue() == null ? Instant.now() : getValue().plus(durationBetweenRuns);
    }

    public boolean isAvailable()
    {
        return availableOn().minusMillis(1).isBefore(Instant.now());
    }

    public boolean runIfAvailable()
    {
        if(!isAvailable())
        {
            LOG.trace("Wanted to execute {} timer but there is no need to do it yet", getKey());
            return false;
        }

        task.run();
        this.setValueAndSave(Instant.now());
        LOG.debug("Executed {} timer", getKey());
        return true;
    }

}
