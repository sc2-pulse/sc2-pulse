// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import reactor.util.retry.RetrySpec;

/*
    # Simple rate limiter for Reactor

    * Decorate target monos:
        * mono.retryWhen(rateLimiter.retryWhen(retrySpec));
        * mono.delaySubscription(rateLimiter.requestSlot());
    * Refresh slots:
        * Flux
            .interval(Duration.ofSeconds(0), Duration.ofSeconds(1))
            .doOnNext(i->rateLimiter.refreshSlots(slotCount))
            .subscribe();
 */
public class ReactorRateLimiter
{

    private static final Logger LOG = LoggerFactory.getLogger(ReactorRateLimiter.class);

    private final ConcurrentLinkedQueue<MonoSink<Void>> requests = new ConcurrentLinkedQueue<>();

    private final AtomicInteger slots = new AtomicInteger(0);
    private final AtomicBoolean isResetActive = new AtomicBoolean(false);
    private RateLimitRefreshConfig refreshConfig;
    private Disposable refreshSubscription;

    private RateLimitData lastData;

    public ReactorRateLimiter(RateLimitRefreshConfig refreshConfig)
    {
        setRefreshConfig(refreshConfig);
    }

    public ReactorRateLimiter()
    {
        this(null);
    }

    public void setRefreshConfig(RateLimitRefreshConfig config)
    {
        if(refreshSubscription != null) refreshSubscription.dispose();
        if(config != null) Flux.interval(Duration.ofSeconds(0), config.getPeriod())
            .subscribe(i->refreshSlots(config.getSlotsPerPeriod()));
    }

    public RateLimitRefreshConfig getRefreshConfig()
    {
        return refreshConfig;
    }

    public RateLimitData getLastData()
    {
        return lastData;
    }

    public boolean isResetActive()
    {
        return isResetActive.get();
    }

    public Mono<Void> update(RateLimitData rateLimitData)
    {
        if(lastData != null && rateLimitData.getReset().compareTo(lastData.getReset()) <= 0)
            return Mono.empty();
        if(!isResetActive.compareAndSet(false, true))
            return Mono.empty();

        lastData = rateLimitData;
        Duration delay =
            Duration.ofMillis(Math.max(rateLimitData.getReset().toEpochMilli() - System.currentTimeMillis(), 0));
        LOG.trace("Will grant {} slots in {}", rateLimitData.getLimit(), delay);
        return Mono
            .delay(delay)
            .then(Mono.fromRunnable
            (
                ()->
                {
                    isResetActive.set(false);
                    refreshSlots(rateLimitData.getLimit());
                }
            ));
    }

    /**
     * This method should be called in a loop if rate limiter is supposed to be updated via
     * {@link #update(RateLimitData)}. It will grant the {@link RateLimitData#getLimit()} of the
     * {@link #getLastData()} or {@code suggestedSlotCount} slots if rate limiter state is
     * undetermined. Undetermined rate limiter is a limiter that has no information about rate
     * limit in the current period. This may happen due to network errors. This is a fallback
     * refresh method.
     *
     * @param threshold min duration since last reset via {@link #update(RateLimitData)}
     * @param suggestedSlotCount this number will be used if {@link #getLastData()} is null
     * @return true if slots were refreshed, false otherwise
     */
    public boolean refreshUndeterminedSlots(Duration threshold, int suggestedSlotCount)
    {
        if(!isResetActive.compareAndSet(false, true)) return false;

        boolean refreshed = false;
        if(lastData == null || lastData.getReset().isBefore(Instant.now().minus(threshold)))
        {
            refreshSlots(lastData != null ? lastData.getLimit() : suggestedSlotCount);
            refreshed = true;
        }
        isResetActive.set(false);
        return refreshed;
    }

    public void refreshSlots(int count)
    {
        int originalCount = count;
        MonoSink<Void> request;
        while(count > 0 && (request = requests.poll()) != null)
        {
            request.success();
            count--;
        }
        slots.getAndSet(count);
        LOG.trace("Slots granted: {}, current slots: {}, queue: {}", originalCount, count, requests.size());
    }

    public Mono<Void> requestSlot()
    {
        if(isSlotAvailable()) return Mono.empty();

        return Mono.create(requests::add);
    }

    /**
     * This method is exposed for tests only. Don't use it because it decreases the number of
     * available slots due to the nature of non-blocking atomic operations.
     *
     * @return true if there is at least 1 slot available, false otherwise
     */
    protected boolean isSlotAvailable()
    {
        return slots.addAndGet(-1) >= 0;
    }

    public int getAvailableSlots()
    {
        return slots.get();
    }

    public Retry retryWhen(RetrySpec retrySpec)
    {
        return retrySpec.doBeforeRetryAsync(s->requestSlot());
    }

    public Retry retryWhen(RetryBackoffSpec retrySpec)
    {
        return retrySpec.doBeforeRetryAsync(s->requestSlot());
    }

}
