// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import reactor.util.retry.RetrySpec;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
        if(slots.addAndGet(-1) >= 0) return Mono.empty();

        return Mono.create(requests::add);
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
