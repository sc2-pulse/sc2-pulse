// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

    private static List<Season> seasons;

    @BeforeAll
    public static void beforeAll(@Autowired DataSource dataSource, @Autowired SeasonDAO seasonDAO)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        OffsetDateTime start = SC2Pulse.offsetDateTime(2020, 1, 1);
        seasons = new ArrayList<>();
        for(Region region : Region.values())
            seasons.add(seasonDAO.create(new Season(null, 1, region, 2020, 1,
                start, start.plusMonths(1))));
        seasons.add(seasonDAO.create(new Season(null, 2, Region.EU, 2020, 2,
            start.plusMonths(1), start.plusMonths(2))));
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
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

    @Test
    public void testFindByLastBattlenetId()
    {
        List<Season> seasons = seasonDAO.findListByBattlenetId(1);
        assertEquals(Region.values().length, seasons.size());
        assertTrue(seasons.stream().allMatch(s->s.getBattlenetId() == 1));
    }

    @Test
    public void testFindAllSeasons()
    {
        List<Season> seasons = seasonDAO.findListByBattlenetId(null);
        assertEquals(Region.values().length + 1, seasons.size());
        verifySeason(seasons.get(0), Region.EU, 2);
        Arrays.stream(Region.values())
            .map(Region::ordinal)
            .sorted(Comparator.reverseOrder())
            .forEach(i->verifySeason(seasons.get(seasons.size() - i - 1), Region.values()[i], 1));
    }

    @Test
    public void testFindByRegionAndBattlenetId()
    {
        Season season = seasonDAO.find(Region.EU, 1).orElseThrow();
        Season expected = seasons.stream()
            .filter(s->s.getRegion() == Region.EU && season.getBattlenetId() == 1)
            .findFirst()
            .orElseThrow();
        Assertions.assertThat(season).isEqualTo(expected);
    }

    private void verifySeason(Season season, Region region, int battlenetId)
    {
        assertEquals(region, season.getRegion());
        assertEquals(battlenetId, season.getBattlenetId());
    }

}
