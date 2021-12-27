// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class APIHealthMonitorTest
{

    @Test
    public void testHealthStats()
    {
        APIHealthMonitor monitor = new APIHealthMonitor(mock(VarDAO.class), "prefix");
        assertEquals(0, monitor.getRequests());
        assertEquals(0, monitor.getErrors());
        assertEquals(0, monitor.getErrorRate());
        assertEquals(100, monitor.getHealth());

        monitor.addRequest();
        monitor.addRequest();
        monitor.addRequest();

        monitor.addError();

        assertEquals(3, monitor.getRequests());
        assertEquals(1, monitor.getErrors());

        monitor.update();

        assertEquals(0, monitor.getRequests());
        assertEquals(0, monitor.getErrors());
        assertEquals(33.33333333333333, monitor.getErrorRate());
        assertEquals(66.66666666666667, monitor.getHealth());
    }

}
