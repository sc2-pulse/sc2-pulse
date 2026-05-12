// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class TimerVarIT
{

    @Autowired
    private VarDAO varDAO;

    @Test
    public void testDurationBetweenRunsPersistence()
    {
        Duration defaultDuration = Duration.ofDays(1);
        TimerVar var = new TimerVar(varDAO, "timer-var-test", true, defaultDuration, ()->{});
        assertEquals(defaultDuration, var.getDurationBetweenRuns());
        var.save();
        Duration newDuration1 = defaultDuration.plusDays(1);
        var.setDurationBetweenRuns(newDuration1);
        assertEquals(newDuration1, var.getDurationBetweenRuns());
        var.load();
        assertEquals(defaultDuration, var.getDurationBetweenRuns());
    }

}
