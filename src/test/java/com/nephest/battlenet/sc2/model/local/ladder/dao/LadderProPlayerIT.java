// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
public class LadderProPlayerIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired Client clickHouseClient
    )
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
    public void whenNoLinks_thenReturnEmptyCollection()
    {
        ProPlayer proPlayer = proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        LadderProPlayer ladderProPlayer = ladderProPlayerDAO.findByIds(Set.of(proPlayer.getId()))
            .iterator().next();
        assertNotNull(ladderProPlayer.getLinks());
        assertTrue(ladderProPlayer.getLinks().isEmpty());
    }

}
