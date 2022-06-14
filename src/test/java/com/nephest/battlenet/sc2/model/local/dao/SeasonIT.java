// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SeasonIT
{

    @Autowired
    private SeasonDAO seasonDAO;

    @BeforeEach
    public void beforeAll(@Autowired DataSource dataSource, @Autowired SeasonDAO seasonDAO)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        LocalDate start = LocalDate.of(2020, 1, 1);
        for(Region region : Region.values())
            seasonDAO.create(new Season(null, 1, region, 2020, 1, start, start.plusMonths(1)));
        seasonDAO.create(new Season(null, 2, Region.EU, 2020, 2,
            start.plusMonths(1), start.plusMonths(2)));
    }

    @AfterEach
    public void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindLastInAllRegions()
    {
        List<Season> lastSeasons = seasonDAO.findLastInAllRegions();
        assertEquals(Region.values().length, lastSeasons.size());
        for(Season season : lastSeasons)
            assertEquals(season.getRegion() == Region.EU ? 2 : 1, season.getBattlenetId());
    }

    @Test
    public void testFindLastBattlenetIdInAllRegions()
    {
        List<Integer> lastSeasons = seasonDAO.getLastInAllRegions();
        assertEquals(2, lastSeasons.size());
        assertTrue(lastSeasons.contains(1));
        assertTrue(lastSeasons.contains(2));
    }

    @Test
    public void testFindLastInRegion()
    {
        assertEquals(2, seasonDAO.getMaxBattlenetId(Region.EU));
        assertEquals(1, seasonDAO.getMaxBattlenetId(Region.US));
    }

}
