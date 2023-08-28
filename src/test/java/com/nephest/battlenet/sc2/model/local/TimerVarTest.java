// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TimerVarTest
{

    private static final String KEY = "timer";
    private static final Duration DEFAULT_DURATION_BETWEEN_TASKS = Duration.ofDays(1);
    private static final int TEST_LAG_SECONDS = 30;

    @Mock
    private Runnable task;

    @Mock
    private VarDAO varDAO;

    private TimerVar timerVar;

    @BeforeEach
    public void beforeEach()
    {
        timerVar = new TimerVar(varDAO, KEY, false, DEFAULT_DURATION_BETWEEN_TASKS, task);
    }

    @Test
    public void whenShouldRun_thenRunAndResetTimer()
    {
        //should run when instant is null
        assertTrue(timerVar.isAvailable());

        when(varDAO.find(KEY))
            .thenReturn(Optional.of(
                String.valueOf(Instant.now().minus(DEFAULT_DURATION_BETWEEN_TASKS).toEpochMilli())));
        timerVar.load();

        assertTrue(timerVar.availableOn().isBefore(Instant.now()));
        assertTrue(timerVar.isAvailable());
        assertTrue(timerVar.runIfAvailable());
        verify(task).run();
        //timer is updated
        assertTrue(timerVar.getValue().isAfter(Instant.now().minusSeconds(TEST_LAG_SECONDS)));
        verify(varDAO).merge(eq(KEY), any());
        assertTrue(timerVar.availableOn().isAfter(Instant.now()));
    }

    @Test
    public void whenShouldNotRun_thenDoNothing()
    {
        when(varDAO.find(KEY))
            .thenReturn(Optional.of(String.valueOf(Instant.now().minus(
                DEFAULT_DURATION_BETWEEN_TASKS).plusSeconds(TEST_LAG_SECONDS).toEpochMilli())));
        timerVar.load();

        assertFalse(timerVar.availableOn().isBefore(Instant.now()));
        assertFalse(timerVar.isAvailable());
        assertFalse(timerVar.runIfAvailable());
        verify(task, never()).run();
        assertFalse(timerVar.getValue().isAfter(Instant.now().minusSeconds(TEST_LAG_SECONDS)));
        verify(varDAO, never()).merge(eq(KEY), any());
        assertFalse(timerVar.availableOn().isBefore(Instant.now()));
    }

    @Test
    public void whenActive_thenDoNothing()
    throws InterruptedException
    {
        doAnswer
        (
            i->
            {
                try
                {
                    Thread.sleep(100);
                    throw new RuntimeException("test");
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        ).when(task).run();

        assertFalse(timerVar.isActive());
        assertTrue(timerVar.isAvailable());
        new Thread(()->timerVar.runIfAvailable()).start();
        Thread.sleep(50);
        assertTrue(timerVar.isActive());
        assertFalse(timerVar.isAvailable());
        Thread.sleep(100);
        assertFalse(timerVar.isActive());
        assertTrue(timerVar.isAvailable());
    }

    @Test
    public void whenValueBeforeTask_thenCalculateValueBeforeTaskWasRan()
    {
        timerVar = new TimerVar(varDAO, KEY, false, DEFAULT_DURATION_BETWEEN_TASKS, task, true);
        assertFalse(timerVar.isActive());
        assertTrue(timerVar.isAvailable());

        Instant[] instants = new Instant[1];
        doAnswer
        (
            i->
            {
                try
                {
                    instants[0] = Instant.now();
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                return null;
            }
        ).when(task).run();

        assertTrue(timerVar.runIfAvailable());
        assertTrue(timerVar.getValue().isBefore(instants[0]));

    }

}
