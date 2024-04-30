// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpdateServiceTest
{

    private UpdateService updateService;

    @BeforeEach
    public void beforeEach()
    {
        updateService = new UpdateService(mock(VarDAO.class));
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
