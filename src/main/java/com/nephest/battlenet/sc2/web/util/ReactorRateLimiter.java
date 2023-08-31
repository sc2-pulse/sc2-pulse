// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
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

    private final String name;
    private final Integer maxRequestCount;
    private final ConcurrentLinkedQueue<Sinks.One<Void>> requests = new ConcurrentLinkedQueue<>();

    private final AtomicInteger slots = new AtomicInteger(0);
    private final AtomicBoolean isResetActive = new AtomicBoolean(false);
    private RateLimitRefreshConfig refreshConfig;
    private Disposable refreshSubscription;
    private final Map<String, ReactorRateLimiter> priorityLimiters = new LinkedHashMap<>();

    private RateLimitData lastData;

    public ReactorRateLimiter(RateLimitRefreshConfig refreshConfig, String name, Integer maxRequestCount)
    {
        if(maxRequestCount != null && maxRequestCount < 1)
            throw new IllegalArgumentException("Request count must be null or greater then 0");

        this.name = name;
        this.maxRequestCount = maxRequestCount;
        setRefreshConfig(refreshConfig);
    }

    public ReactorRateLimiter(String name, Integer maxRequestCount)
    {
        this(null, name, maxRequestCount);
    }

    public ReactorRateLimiter(RateLimitRefreshConfig refreshConfig)
    {
        this(refreshConfig, null, null);
    }

    public ReactorRateLimiter()
    {
        this(null);
    }

    public static Mono<Void> requestSlot(Iterable<ReactorRateLimiter> limiters)
    {
        Mono<Void> result = Mono.empty();
        for(ReactorRateLimiter limiter : limiters)
            result = result.then(limiter.requestSlot());
        return result;
    }

    public static Retry retryWhen(Iterable<ReactorRateLimiter> limiters, RetrySpec retrySpec)
    {
        return retrySpec.doBeforeRetryAsync(s->requestSlot(limiters));
    }

    public String getName()
    {
        return name;
    }

    public Integer getMaxRequestCount()
    {
        return maxRequestCount;
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
        if(count < 1) return;
        count = refreshPrioritySlots(count);
        if(count < 1) return;

        int originalCount = count;
        Sinks.One<Void> request;
        while(count > 0 && (request = requests.poll()) != null)
        {
            request.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
            count--;
        }
        slots.getAndSet(count);
        LOG.trace("Slots granted: {}, current slots: {}, queue: {}", originalCount, count, requests.size());
    }

    private int refreshPrioritySlots(int count)
    {
        if(priorityLimiters.isEmpty()) return count;

        int slotsLeft = count;
        for(ReactorRateLimiter limiter : priorityLimiters.values())
        {
            int maxSlots = limiter.getMaxRequestCount() != null
                ? limiter.getMaxRequestCount() - Math.max(limiter.getAvailableSlots(), 0)
                : slotsLeft;
            int slotsGranted = Math.min(maxSlots, slotsLeft);
            if(slotsGranted == 0) continue;

            limiter.refreshSlots(slotsGranted);
            slotsLeft -= slotsGranted;
            if(slotsLeft == 0) break;
        }
        //use remaining slots if there are enough slots in the parent limiter
        if(slotsLeft > 0 && slotsLeft != count && getAvailableSlots() >= slotsLeft)
            return refreshPrioritySlots(slotsLeft);
        return slotsLeft;
    }

    public Mono<Void> requestSlot()
    {
        if(isSlotAvailable()) return Mono.empty();

        Sinks.One<Void> one = Sinks.one();
        requests.add(one);
        return one.asMono();
    }

    public Mono<Void> requestSlot(String name)
    {
        ReactorRateLimiter limiter = priorityLimiters.get(name);
        if(limiter == null) throw new IllegalStateException("Limiter not found: " + name);

        return limiter.requestSlot();
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

    public void addPriorityLimiter(ReactorRateLimiter limiter)
    {
        priorityLimiters.put(limiter.getName(), limiter);
    }

    public ReactorRateLimiter getPriorityLimiter(String name)
    {
        return priorityLimiters.get(name);
    }

}
