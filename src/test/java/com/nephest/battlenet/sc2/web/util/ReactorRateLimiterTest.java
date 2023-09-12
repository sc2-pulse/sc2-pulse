// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetrySpec;

public class ReactorRateLimiterTest
{

    private ReactorRateLimiter limiter;

    @BeforeEach
    public void beforeEach()
    {
        limiter = new ReactorRateLimiter();
    }

    @Test
    public void testUpdateByRateLimitData()
    {
        assertEquals(0, limiter.getAvailableSlots());
        Instant initialReset = Instant.now();
        limiter.update(new RateLimitData(10, 5, initialReset)).block();
        assertEquals(10, limiter.getAvailableSlots());

        //rate limit data from the same cycle(reset) is ignored
        limiter.update(new RateLimitData(20, 20, initialReset)).block();
        assertEquals(10, limiter.getAvailableSlots());

        //rate limit data from previous cycles(resets) is ignored
        limiter.update(new RateLimitData(20, 20, initialReset.minusSeconds(1))).block();
        assertEquals(10, limiter.getAvailableSlots());

        Mono<Void> update = limiter
            .update(new RateLimitData(20, 15, Instant.now().plusMillis(500)));
        //not updated yet
        assertEquals(10, limiter.getAvailableSlots());
        update.block();
        //updated
        assertEquals(20, limiter.getAvailableSlots());
    }

    @Test
    public void whenLastDataIsNull_thenRefreshUndeterminedSlots()
    {
        limiter.refreshUndeterminedSlots(Duration.ofDays(1), 50);
        assertEquals(50, limiter.getAvailableSlots());
    }

    @Test
    public void whenRefreshIsActive_thenDontRefreshUndeterminedSlots()
    {
        limiter.update(new RateLimitData(10, 5, Instant.now().plusSeconds(10)));
        assertFalse(limiter.refreshUndeterminedSlots(Duration.ofSeconds(-100), 50));
        assertEquals(0, limiter.getAvailableSlots());
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testRefreshUndeterminedSlots(boolean thresholdReached)
    {
        Duration threshold = Duration.ofMinutes(1);
        Instant initialReset = Instant.now().minus(threshold);
        limiter.update(new RateLimitData(10, 5, initialReset)).block();
        limiter.requestSlot();
        assertEquals(9, limiter.getAvailableSlots());

        limiter.refreshUndeterminedSlots(thresholdReached ? threshold : threshold.plusSeconds(10), 50);
        //The limit of last RateLimitData is used when possible
        assertEquals(thresholdReached ? 10 : 9, limiter.getAvailableSlots());
    }

    @Test
    public void testRefreshConfig()
    {
        Duration period = Duration.ofMillis(100);
        RateLimitRefreshConfig config = new RateLimitRefreshConfig(period, 1);
        ReactorRateLimiter limiter = new ReactorRateLimiter(config);
        limiter.requestSlot().block();
        Instant begin = Instant.now();
        limiter.requestSlot().block();
        Duration realPeriod = Duration.between(begin, Instant.now());
        assertTrue(realPeriod.compareTo(period.multipliedBy(2)) <= 0);
    }

    @Test
    public void testRequestMultiLimiterSlot()
    {
        ReactorRateLimiter limiter1 = new ReactorRateLimiter();
        ReactorRateLimiter limiter2 = new ReactorRateLimiter();
        limiter1.refreshSlots(10);
        limiter2.refreshSlots(5);
        ReactorRateLimiter.requestSlot(List.of(limiter1, limiter2)).block(Duration.ofMillis(1));
        assertEquals(9, limiter1.getAvailableSlots());
        assertEquals(4, limiter2.getAvailableSlots());
    }

    @Test
    public void whenPriorityLimiterExists_thenUseIt()
    {
        ReactorRateLimiter limiter1 = new ReactorRateLimiter();
        ReactorRateLimiter limiter2 = new ReactorRateLimiter();
        ReactorRateLimiter priorityLimiter1 = new ReactorRateLimiter("priority1", 2);
        limiter1.addPriorityLimiter(priorityLimiter1);
        limiter1.refreshSlots(10);
        limiter2.refreshSlots(5);
        ReactorRateLimiter.requestSlot(List.of(limiter1, limiter2), "priority1")
            .block(Duration.ofMillis(1));
        assertEquals(8, limiter1.getAvailableSlots());
        assertEquals(1, priorityLimiter1.getAvailableSlots());
        assertEquals(4, limiter2.getAvailableSlots());
    }

    @ValueSource(ints = {0, 1})
    @ParameterizedTest
    public void whenRequestMultiLimiterSlot_thenAllLimitersShouldBeUsed(int activeIx)
    {
        ReactorRateLimiter limiter1 = new ReactorRateLimiter();
        ReactorRateLimiter limiter2 = new ReactorRateLimiter();
        List<ReactorRateLimiter> limiters = List.of(limiter1, limiter2);
        limiters.get(activeIx).refreshSlots(2);
        assertThrows
        (
            IllegalStateException.class,
            ()->ReactorRateLimiter.requestSlot(limiters).block(Duration.ofMillis(1))
        );
        assertThrows
        (
            IllegalStateException.class,
            ()->Mono.error(new RuntimeException("test"))
                .retryWhen(ReactorRateLimiter.retryWhen(limiters, RetrySpec.max(2)))
                .onErrorComplete()
                .block(Duration.ofMillis(1))
        );
    }

    @Test
    public void testMultiLimiterRetry()
    {
        ReactorRateLimiter limiter1 = new ReactorRateLimiter();
        ReactorRateLimiter limiter2 = new ReactorRateLimiter();
        List<ReactorRateLimiter> limiters = List.of(limiter1, limiter2);
        Retry retry = ReactorRateLimiter.retryWhen(limiters, RetrySpec.max(2));
        limiter1.refreshSlots(10);
        limiter2.refreshSlots(5);
        Mono.error(new RuntimeException("test"))
            .retryWhen(retry)
            .onErrorComplete()
            .block(Duration.ofMillis(1));
        assertEquals(8, limiter1.getAvailableSlots());
        assertEquals(3, limiter2.getAvailableSlots());
    }

    @Test
    public void whenPriorityRetryLimiterExists_thenUseIt()
    {
        ReactorRateLimiter limiter1 = new ReactorRateLimiter();
        ReactorRateLimiter limiter2 = new ReactorRateLimiter();
        ReactorRateLimiter priorityLimiter1 = new ReactorRateLimiter("priority1", 2);
        limiter1.addPriorityLimiter(priorityLimiter1);
        List<ReactorRateLimiter> limiters = List.of(limiter1, limiter2);
        Retry retry = ReactorRateLimiter.retryWhen(limiters, RetrySpec.max(2), "priority1");
        limiter1.refreshSlots(10);
        limiter2.refreshSlots(5);
        Mono.error(new RuntimeException("test"))
            .retryWhen(retry)
            .onErrorComplete()
            .block(Duration.ofMillis(1));
        assertEquals(8, limiter1.getAvailableSlots());
        assertEquals(0, priorityLimiter1.getAvailableSlots());
        assertEquals(3, limiter2.getAvailableSlots());
    }

    @CsvSource
    ({
        "10, 3, 2, 3, 2, 5",
        "4, 3, 2, 3, 1, 0",
        "2, 3, 2, 2, 0, 0",
        "0, 1, 1, 0, 0, 0"
    })
    @ParameterizedTest
    public void whenPriorityRateLimiterExists_thenUseItBeforeParent
    (
        int slots,
        int priority1MaxSlots,
        int priority2MaxSlots,
        int expectedPrioritySlots1,
        int expectedPrioritySlots2,
        int expectedGeneralSlots
    )
    {
        ReactorRateLimiter limiter = new ReactorRateLimiter();
        limiter.addPriorityLimiter(new ReactorRateLimiter("priority1", priority1MaxSlots));
        limiter.addPriorityLimiter(new ReactorRateLimiter("priority2", priority2MaxSlots));
        ReactorRateLimiter priorityLimiter1 = limiter.getPriorityLimiter("priority1");
        ReactorRateLimiter priorityLimiter2 = limiter.getPriorityLimiter("priority2");

        assertEquals(0, priorityLimiter1.getAvailableSlots());
        assertEquals(0, priorityLimiter2.getAvailableSlots());
        assertEquals(0, limiter.getAvailableSlots());

        limiter.refreshSlots(slots);
        assertEquals(expectedPrioritySlots1, priorityLimiter1.getAvailableSlots());
        assertEquals(expectedPrioritySlots2, priorityLimiter2.getAvailableSlots());
        assertEquals(expectedGeneralSlots, limiter.getAvailableSlots());
    }

    @Test
    public void testRequestPrioritySlot()
    {
        ReactorRateLimiter priorityLimiter1
            = new ReactorRateLimiter("priority1", 2);
        ReactorRateLimiter priorityLimiter2
            = new ReactorRateLimiter("priority2", 3);
        ReactorRateLimiter limiter = new ReactorRateLimiter();
        limiter.addPriorityLimiter(priorityLimiter1);
        limiter.addPriorityLimiter(priorityLimiter2);

        assertEquals(0, priorityLimiter1.getAvailableSlots());
        assertEquals(0, priorityLimiter2.getAvailableSlots());
        assertEquals(0, limiter.getAvailableSlots());

        limiter.refreshSlots(10);
        assertEquals(2, priorityLimiter1.getAvailableSlots());
        assertEquals(3, priorityLimiter2.getAvailableSlots());
        assertEquals(5, limiter.getAvailableSlots());

        limiter.requestSlot(priorityLimiter1.getName());
        assertEquals(1, priorityLimiter1.getAvailableSlots());
        assertEquals(3, priorityLimiter2.getAvailableSlots());
        assertEquals(5, limiter.getAvailableSlots());
    }

    @Test
    public void whenThereIsEnoughGeneralSlots_thenUseNewSlotsForPriorityLimiters()
    {
        ReactorRateLimiter limiter = new ReactorRateLimiter();
        limiter.addPriorityLimiter(new ReactorRateLimiter("priority1", 2));
        limiter.addPriorityLimiter(new ReactorRateLimiter("priority2", 1));
        ReactorRateLimiter priorityLimiter1 = limiter.getPriorityLimiter("priority1");
        ReactorRateLimiter priorityLimiter2 = limiter.getPriorityLimiter("priority2");

        assertEquals(0, priorityLimiter1.getAvailableSlots());
        assertEquals(0, priorityLimiter2.getAvailableSlots());
        assertEquals(0, limiter.getAvailableSlots());

        limiter.refreshSlots(10);
        assertEquals(2, priorityLimiter1.getAvailableSlots());
        assertEquals(1, priorityLimiter2.getAvailableSlots());
        assertEquals(7, limiter.getAvailableSlots());


        Flux<Void> slots1 = Flux.fromIterable
        (
            IntStream.range(0, 5)
                .boxed()
                .map(i->limiter.requestSlot("priority1"))
                .collect(Collectors.toList())
        )
            .flatMap(Function.identity());
        Flux<Void> slots2 = Flux.fromIterable
        (
            IntStream.range(0, 1)
                .boxed()
                .map(i->limiter.requestSlot("priority2"))
                .collect(Collectors.toList())
        )
            .flatMap(Function.identity());
        limiter.refreshSlots(7);
        assertEquals(1, priorityLimiter1.getAvailableSlots());
        assertEquals(1, priorityLimiter2.getAvailableSlots());
        assertEquals(7, limiter.getAvailableSlots());
        slots1.blockLast();
        slots2.blockLast();
    }

    @Test
    public void whenGettingNumberOfAvailableSlots_thenTakeIntoAccountSlotQueue()
    {
        ReactorRateLimiter limiter = new ReactorRateLimiter();
        for(int i = 0; i < 10; i++) limiter.requestSlot();
        limiter.refreshSlots(3);
        assertEquals(-7, limiter.getAvailableSlots());
    }

    @Test
    public void testRefreshFloatSlots()
    {
        DecimalFormat format = new DecimalFormat("#.#");
        ReactorRateLimiter limiter = new ReactorRateLimiter();
        assertEquals(0, limiter.getAvailableSlots());
        assertEquals(0.0f, limiter.getSlotDecimal());

        limiter.refreshSlots(0.5f);
        assertEquals(0, limiter.getAvailableSlots());
        assertEquals(0.5f, limiter.getSlotDecimal());

        limiter.refreshSlots(0.5f);
        assertEquals(1, limiter.getAvailableSlots());
        assertEquals(0f, limiter.getSlotDecimal());

        limiter.refreshSlots(2.3f);
        assertEquals(2, limiter.getAvailableSlots());
        assertEquals("0.3", format.format(limiter.getSlotDecimal()));

        limiter.refreshSlots(3.8f);
        assertEquals(4, limiter.getAvailableSlots());
        assertEquals("0.1", format.format(limiter.getSlotDecimal()));
    }

}
