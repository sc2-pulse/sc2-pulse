// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

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

}
