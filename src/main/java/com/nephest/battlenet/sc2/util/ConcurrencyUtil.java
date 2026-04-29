// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class ConcurrencyUtil
{

    private ConcurrencyUtil()
    {
    }

    public static final ThreadPoolTaskExecutor THREAD_POOL_TASK_EXECUTOR;
    static
    {
        THREAD_POOL_TASK_EXECUTOR = new ThreadPoolTaskExecutor();
        THREAD_POOL_TASK_EXECUTOR.setCorePoolSize(4);
        THREAD_POOL_TASK_EXECUTOR.setMaxPoolSize(150);
        THREAD_POOL_TASK_EXECUTOR.setQueueCapacity(0);
        THREAD_POOL_TASK_EXECUTOR.setKeepAliveSeconds(600);
        THREAD_POOL_TASK_EXECUTOR.setThreadNamePrefix("async-");
        THREAD_POOL_TASK_EXECUTOR.initialize();
    }
    public static final Executor DIRECT_EXECUTOR = Runnable::run;

    public static <I, C extends Collection<I>> List<C> splitIntoBatches
    (
        C all,
        int batchSize,
        Function<Integer, C> batchCollectionSupplier
    )
    {
        if(batchSize < 1) throw new IllegalArgumentException("Positive batchSize expected");
        if(all.isEmpty()) return List.of();
        if(all.size() <= batchSize) return List.of(all);

        int expectedBatchesSize = (all.size() + batchSize - 1) / batchSize;
        List<C> batches = new ArrayList<>(expectedBatchesSize);
        C batch = batchCollectionSupplier.apply(batchSize);
        for(I item : all)
        {
            batch.add(item);
            if(batch.size() == batchSize)
            {
                batches.add(batch);
                if(batches.size() < expectedBatchesSize) batch = batchCollectionSupplier.apply(batchSize);
            }
        }
        if (batches.size() < expectedBatchesSize) batches.add(batch);
        return batches;
    }

    public static <I, O, C extends Collection<I>> List<O> batchCall
    (
        C all,
        int batchSize,
        Function<Integer, C> batchCollectionSupplier,
        Function<C, O> call,
        Executor executor
    )
    {
        if(batchSize < 1) throw new IllegalArgumentException("Positive batchSize expected");
        if(all.isEmpty()) return List.of();

        if(all.size() <= batchSize) return List.of(call.apply(all));

        List<C> batches = splitIntoBatches(all, batchSize, batchCollectionSupplier);
        List<CompletableFuture<O>> futures = new ArrayList<>(batches.size());
        for(int i = 0; i < batches.size() - 1; i++)
        {
            C batch = batches.get(i);
            futures.add(CompletableFuture.supplyAsync(()->call.apply(batch), executor));
        }
        C lastBatch = batches.get(batches.size() - 1);
        futures.add(CompletableFuture.supplyAsync(()->call.apply(lastBatch), DIRECT_EXECUTOR));

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v->futures.stream().map(CompletableFuture::join).toList())
            .join();
    }

    public static <I, O, C extends Collection<I>> List<O> batchCall
    (
        C all,
        int batchSize,
        Function<Integer, C> batchCollectionSupplier,
        Function<C, O> call
    )
    {
        return batchCall(all, batchSize, batchCollectionSupplier, call, DIRECT_EXECUTOR);
    }

    public static <I, E, O extends Collection<E>, C extends Collection<I>> List<E> batchCallFlatMap
    (
        C all,
        int batchSize,
        Function<Integer, C> batchCollectionSupplier,
        Function<C, O> call,
        Executor executor
    )
    {
        return batchCall(all, batchSize, batchCollectionSupplier, call, executor).stream()
            .flatMap(Collection::stream)
            .toList();
    }

    public static <I, E, O extends Collection<E>, C extends Collection<I>> List<E> batchCallFlatMap
    (
        C all,
        int batchSize,
        Function<Integer, C> batchCollectionSupplier,
        Function<C, O> call
    )
    {
        return batchCallFlatMap(all, batchSize, batchCollectionSupplier, call, DIRECT_EXECUTOR);
    }

}
