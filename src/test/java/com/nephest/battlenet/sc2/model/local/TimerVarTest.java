// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

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

    private void defaultLoad()
    {
        //should run when instant is null
        assertTrue(timerVar.isAvailable());

        when(varDAO.find(KEY))
            .thenReturn(Optional.of(
                String.valueOf(SC2Pulse.instant().minus(DEFAULT_DURATION_BETWEEN_TASKS).toEpochMilli())));
        when(varDAO.find(KEY + TimerVar.DURATION_BETWEEN_TASKS_SUFFIX))
            .thenReturn(Optional.of(DEFAULT_DURATION_BETWEEN_TASKS.toString()));
        timerVar.load();
    }

    @Test
    public void whenShouldRun_thenRunAndResetTimer()
    {
        defaultLoad();

        assertTrue(timerVar.availableOn().isBefore(SC2Pulse.instant()));
        assertTrue(timerVar.isAvailable());
        //should be hot mono to prevent side effects
        Mono<Boolean> taskMono = timerVar.runIfAvailable();
        assertEquals(taskMono, timerVar.getLastTask());
        assertTrue(taskMono.block());
        assertTrue(taskMono.block());
        verify(task, times(1)).run();
        //timer is updated
        assertTrue(timerVar.getValue().isAfter(SC2Pulse.instant().minusSeconds(TEST_LAG_SECONDS)));
        verify(varDAO, times(1)).merge(eq(KEY), any());
        assertTrue(timerVar.availableOn().isAfter(SC2Pulse.instant()));
    }

    @Test
    public void whenTaskThrowsException_thenResetActiveFlag_AndDoNotResetTimer()
    {
        doThrow(new RuntimeException("test")).when(task).run();
        defaultLoad();

        assertTrue(timerVar.availableOn().isBefore(SC2Pulse.instant()));
        assertTrue(timerVar.isAvailable());
        assertThrows(RuntimeException.class, ()->timerVar.runIfAvailable().block(), "test");
        verify(task).run();

        assertFalse(timerVar.getValue().isAfter(SC2Pulse.instant().minusSeconds(TEST_LAG_SECONDS)));
        verify(varDAO, never()).merge(eq(KEY), any());
        assertFalse(timerVar.availableOn().isAfter(SC2Pulse.instant()));
        assertFalse(timerVar.isActive());
    }

    @Test
    public void whenShouldNotRun_thenDoNothing()
    {
        when(varDAO.find(KEY))
            .thenReturn(Optional.of(String.valueOf(SC2Pulse.instant().minus(
                DEFAULT_DURATION_BETWEEN_TASKS).plusSeconds(TEST_LAG_SECONDS).toEpochMilli())));
        when(varDAO.find(KEY + TimerVar.DURATION_BETWEEN_TASKS_SUFFIX))
            .thenReturn(Optional.of(DEFAULT_DURATION_BETWEEN_TASKS.toString()));
        timerVar.load();

        assertFalse(timerVar.availableOn().isBefore(SC2Pulse.instant()));
        assertFalse(timerVar.isAvailable());
        assertFalse(timerVar.runIfAvailable().block());
        verify(task, never()).run();
        assertFalse(timerVar.getValue().isAfter(SC2Pulse.instant().minusSeconds(TEST_LAG_SECONDS)));
        verify(varDAO, never()).merge(eq(KEY), any());
        assertFalse(timerVar.availableOn().isBefore(SC2Pulse.instant()));
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
        new Thread(()->timerVar.runIfAvailable().block()).start();
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
                    Thread.sleep(100);
                    instants[0] = SC2Pulse.instant();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                return null;
            }
        ).when(task).run();

        assertTrue(timerVar.runIfAvailable().block());
        assertTrue(timerVar.getValue().isBefore(instants[0]));

    }

    @Test
    public void whenDurationBetweenTasksIsNull_thenReturnDefaultDuration()
    {
        timerVar = new TimerVar(varDAO, KEY, false, DEFAULT_DURATION_BETWEEN_TASKS, task);
        timerVar.setDurationBetweenRuns(null);
        assertEquals(DEFAULT_DURATION_BETWEEN_TASKS, timerVar.getDurationBetweenRuns());
    }

    @Test
    public void verifyElasticThread()
    {
        String[] threadName = new String[1];
        timerVar = new TimerVar
        (
            varDAO, KEY, false, DEFAULT_DURATION_BETWEEN_TASKS,
            ()->threadName[0] = Thread.currentThread().getName()
        );
        timerVar.runIfAvailable().block();
        assertTrue(threadName[0].toLowerCase().contains("elastic"));
    }

}
