// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * This var encapsulates common logic for simple timer based tasks(run every x duration).
 */
public class TimerVar
extends InstantVar
{

    private static final Logger LOG = LoggerFactory.getLogger(TimerVar.class);
    public static final String DURATION_BETWEEN_TASKS_SUFFIX = ".duration-between-runs";

    private final Duration defaultDurationBetweenRuns;
    private final DurationVar durationBetweenRuns;
    private final Supplier<Mono<?>> task;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final boolean valueBeforeTask;

    public TimerVar
    (
        VarDAO varDAO,
        String key,
        boolean load,
        Duration defaultDurationBetweenRuns,
        Supplier<Mono<?>> task,
        boolean valueBeforeTask
    )
    {
        super(varDAO, key, load);
        this.defaultDurationBetweenRuns = defaultDurationBetweenRuns;
        this.durationBetweenRuns = new DurationVar(varDAO, key + DURATION_BETWEEN_TASKS_SUFFIX, load);
        if(durationBetweenRuns.getValue() == null)
            durationBetweenRuns.setValue(defaultDurationBetweenRuns);
        this.task = task;
        this.valueBeforeTask = valueBeforeTask;
    }

    public TimerVar
    (
        VarDAO varDAO,
        String key,
        boolean load,
        Duration defaultDurationBetweenRuns,
        Runnable task,
        boolean valueBeforeTask
    )
    {
        this
        (
            varDAO,
            key,
            load,
            defaultDurationBetweenRuns,
            ()->Mono.fromRunnable(task).subscribeOn(Schedulers.boundedElastic()),
            valueBeforeTask
        );
    }

    public TimerVar
    (
        VarDAO varDAO,
        String key,
        boolean load,
        Duration durationBetweenRuns,
        Runnable task
    )
    {
        this(varDAO, key, load, durationBetweenRuns, task, false);
    }

    @Override
    public Instant load()
    {
        if(durationBetweenRuns != null) durationBetweenRuns.load();
        return super.load();
    }

    @Override
    public boolean tryLoad()
    {
        return super.tryLoad() & durationBetweenRuns.tryLoad();
    }

    @Override
    public void save()
    {
        durationBetweenRuns.save();
        super.save();
    }

    public Duration getDurationBetweenRuns()
    {
        return durationBetweenRuns.getValue() != null
            ? durationBetweenRuns.getValue()
            : defaultDurationBetweenRuns;
    }

    public void setDurationBetweenRuns(Duration duration)
    {
        durationBetweenRuns.setValue(duration);
    }

    public Instant availableOn()
    {
        return getValue() == null ? SC2Pulse.instant() : getValue().plus(getDurationBetweenRuns());
    }

    public boolean isAvailable()
    {
        return availableOn().minusMillis(1).isBefore(SC2Pulse.instant()) && !isActive();
    }

    public boolean isActive()
    {
        return active.get();
    }

    public boolean isValueBeforeTask()
    {
        return valueBeforeTask;
    }

    public Mono<Boolean> runIfAvailable()
    {
        if(!isAvailable() || !active.compareAndSet(false, true))
        {
            LOG.trace("Wanted to execute {} timer but there is no need to do it yet", getKey());
            return Mono.just(false);
        }

        Instant beforeTask = SC2Pulse.instant();
        return task
            .get()
            .doOnError(e->active.compareAndSet(true, false))
            .then(Mono.fromRunnable(()->{
                this.setValueAndSave(isValueBeforeTask() ? beforeTask : SC2Pulse.instant());
                LOG.debug("Executed {} timer", getKey());
                active.compareAndSet(true, false);
            }))
            .thenReturn(true);
    }

}
