// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class ProgrammaticFluxTest
{

    @Test
    public void testSuppliedConsumer()
    {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        ProgrammaticFlux<Integer> pFlux = new ProgrammaticFlux<>(atomicInteger::set);
        pFlux.getSink()
            .next(100)
            .next(101);
        StepVerifier.create(pFlux.getFlux())
            .expectNext(100)
            .expectNext(101)
            .expectNoEvent(Duration.ofMillis(1));
        assertEquals(101, atomicInteger.get());
    }

    @Test
    public void testNoSuppliedConsumer()
    {
        ProgrammaticFlux<Integer> pFlux = new ProgrammaticFlux<>();
        pFlux.getFlux().subscribe(); //subscribe to activate sink
        int val = 100;
        pFlux.getSink().next(val);
        StepVerifier.create(pFlux.getFlux())
            .expectNext(val)
            .expectNoEvent(Duration.ofMillis(1));
    }

}
