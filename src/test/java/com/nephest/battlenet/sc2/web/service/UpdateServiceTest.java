// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class UpdateServiceTest
{

    @Mock
    private VarDAO varDAO;

    @Mock
    private EventService eventService;

    @Mock
    private LadderUpdateDAO ladderUpdateDAO;

    private UpdateService updateService;

    @BeforeEach
    public void beforeEach()
    {
        when(eventService.getLadderUpdateEvent()).thenReturn(Flux.empty());
        updateService = new UpdateService(varDAO, eventService, ladderUpdateDAO);
    }

    @Test
    public void testUpdateDuration()
    {
        Instant now = SC2Pulse.instant();
        assertEquals(Duration.ZERO, updateService.calculateUpdateDuration(null));

        //there is no previous update, zero duration
        updateService.updated(now.minusSeconds(10000));
        assertEquals(Duration.ZERO, updateService.calculateUpdateDuration(null));

        updateService.updated(now);
        assertEquals(Duration.ofSeconds(10000), updateService.calculateUpdateDuration(null));
    }

}
