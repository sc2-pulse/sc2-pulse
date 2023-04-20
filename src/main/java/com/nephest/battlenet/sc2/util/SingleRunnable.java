// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>
 * This utility class ensures that there can be only one active {@link #runnable} task, as long
 * as it's accessed via the {@link #run()} method.
 * </p>
 * <p>
 * It's useful in a pattern where services signalize that now is a good time to execute a task,
 * but they don't care about the execution flow of the task itself. In this case a new signal can
 * come up before the previous task was executed, which triggers a new task execution. This class
 * solves the issue if you want only one task to be active at any time.
 * </p>
 */
public class SingleRunnable
{

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Runnable runnable;
    private final Executor executor;
    private CompletableFuture<Void> future;

    public SingleRunnable(Runnable runnable)
    {
        this(runnable, null);
    }

    public SingleRunnable(Runnable runnable, Executor executor)
    {
        this.runnable = runnable;
        this.executor = executor;
    }

    /**
     * Run the {@link #runnable} if there is no active task.
     * @return true if new task was scheduled, false is previous task is still active.
     */
    public boolean run()
    {
        if(active.compareAndSet(false, true))
        {
            future =
            (
                executor != null
                    ? CompletableFuture.runAsync(runnable, executor)
                    : CompletableFuture.runAsync(runnable)
            )
                .whenComplete((v, t)->active.set(false));
            return true;
        }
        return false;
    }

    public CompletableFuture<Void> getFuture()
    {
        return future;
    }

    public boolean isActive()
    {
        return active.get();
    }

}
