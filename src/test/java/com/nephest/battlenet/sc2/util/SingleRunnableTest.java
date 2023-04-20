// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class SingleRunnableTest
{

    @Test
    public void testRun()
    {
        AtomicInteger counter = new AtomicInteger();
        Runnable task = ()->
        {
            try
            {
                counter.incrementAndGet();
                Thread.sleep(100);
                throw new RuntimeException("test");
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        };
        SingleRunnable singleRunnable = new SingleRunnable(task);

        assertFalse(singleRunnable.isActive());
        assertTrue(singleRunnable.tryRun());
        assertTrue(singleRunnable.isActive());
        //the task is already active, don't execute a new task
        assertFalse(singleRunnable.tryRun());

        assertThrows(ExecutionException.class, ()->singleRunnable.getFuture().get(), "test");
        assertEquals(1, counter.get());
        //the active status was reset despite the exception
        assertFalse(singleRunnable.isActive());
        //can run a new task now
        assertTrue(singleRunnable.tryRun());
    }

}
