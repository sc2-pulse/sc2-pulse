// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class FastTeamIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private FastTeamDAO fastTeamDAO;

    @BeforeEach
    public void beforeEach
    (
        @Autowired @Qualifier("dataSource") DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterAll
    public static void afterAll
    (
        @Autowired @Qualifier("dataSource") DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testLoad()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            2
        );

        //not loaded yet
        assertFalse(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1204",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );

        assertTrue(fastTeamDAO.load(Region.EU, SeasonGenerator.DEFAULT_SEASON_ID));
        //already loaded
        assertFalse(fastTeamDAO.load(Region.EU, SeasonGenerator.DEFAULT_SEASON_ID));

        assertTrue(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.20.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );
        //US is not loaded
        assertFalse(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.US,
            "1.10.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );

        assertTrue(fastTeamDAO.load(Region.EU, SeasonGenerator.DEFAULT_SEASON_ID + 1));
        //old data was removed
        assertFalse(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.20.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );
    }

    @Test
    public void testMerge()
    {
        Team team1 = new Team
        (
            null,
            1, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
            1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        );
        Team team2 = new Team
        (
            null,
            1, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "2", 1,
            2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        );
        Team[] merge1 = fastTeamDAO.merge(new LinkedHashSet<>(List.of(team1, team2)))
            .stream()
            .sorted(Team.NATURAL_ID_COMPARATOR)
            .toArray(Team[]::new);
        assertEquals(2, merge1.length);
        assertEquals(team1, merge1[0]);
        assertEquals(team2, merge1[1]);

        Team team1_1 = new Team
        (
            null,
            1, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 3,
            3L, 4, 5, 6, 3,
            SC2Pulse.offsetDateTime()
        );
        //no changes in team2
        Team[] merge2 = fastTeamDAO.merge(new LinkedHashSet<>(List.of(team1_1, team2)))
            .toArray(Team[]::new);
        assertEquals(1, merge2.length);
        assertEquals(team1_1, merge2[0]);

        Team foundTeam1 = fastTeamDAO.findById
        (
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1",
            1
        ).orElseThrow();
        assertEquals(team1_1, foundTeam1);
        assertEquals(3, foundTeam1.getDivisionId());
        assertEquals(4, foundTeam1.getWins());
        assertEquals(5, foundTeam1.getLosses());
        assertEquals(6, foundTeam1.getTies());

        Team foundTeam2 = fastTeamDAO.findById
        (
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "2",
            1
        ).orElseThrow();
        assertEquals(team2, foundTeam2);
        assertEquals(1, foundTeam2.getDivisionId());
        assertEquals(2, foundTeam2.getWins());
        assertEquals(2, foundTeam2.getWins());
        assertEquals(2, foundTeam2.getWins());
    }

    @Test
    public void testReload()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            2
        );

        //load
        assertTrue(fastTeamDAO.load(Region.EU, SeasonGenerator.DEFAULT_SEASON_ID));
        assertTrue(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.20.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );

        //clear
        fastTeamDAO.clear(Region.EU);
        assertFalse(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.20.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );

        //load(reload)
        assertTrue(fastTeamDAO.load(Region.EU, SeasonGenerator.DEFAULT_SEASON_ID));
        assertTrue(fastTeamDAO.findById(
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.20.4",
            SeasonGenerator.DEFAULT_SEASON_ID).isPresent()
        );
    }

}
