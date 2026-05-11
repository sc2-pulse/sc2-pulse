// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
public class TimerVarIT
{

    @Autowired
    private VarDAO varDAO;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
    }

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
