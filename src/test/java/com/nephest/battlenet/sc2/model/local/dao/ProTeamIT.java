// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
public class ProTeamIT
{

    @Autowired
    private ProTeamDAO proTeamDAO;

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
    public void testFindByIds()
    {
        ProTeam team1 = proTeamDAO.merge(new ProTeam(null, 1L, "name1", "sn1"));
        ProTeam team2 = proTeamDAO.merge(new ProTeam(null, 2L, "name2", "sn2"));
        ProTeam team3 = proTeamDAO.merge(new ProTeam(null, 3L, "name3", "sn3"));

        List<ProTeam> teams = proTeamDAO.find(Set.of(team1.getId(), team3.getId()));
        assertEquals(2, teams.size());
        teams.sort(Comparator.comparing(ProTeam::getId));
        Assertions.assertThat(teams.get(0))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(team1);
        Assertions.assertThat(teams.get(1))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(team3);
    }

}
