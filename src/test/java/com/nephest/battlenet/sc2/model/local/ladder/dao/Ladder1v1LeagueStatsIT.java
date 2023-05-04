// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class Ladder1v1LeagueStatsIT
{

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired @Qualifier("sc2StatsConversionService")
    ConversionService conversionService;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource
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
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testRaceTeamCount()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU, Region.US),
            Arrays.asList(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10,
            true
        );
        leagueStatsDAO.mergeCalculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);

        //ignore implementation details of the SeasonGenerator
        setLegacyIdRace(Region.EU, 1, 6, Race.TERRAN);
        setLegacyIdRace(Region.EU,6, 14, Race.PROTOSS);
        setLegacyIdRace(Region.EU,14, 24, Race.ZERG);
        setLegacyIdRace(Region.EU,24, 100, Race.RANDOM);
        //test merge
        leagueStatsDAO.mergeCalculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);

        MergedLadderSearchStatsResult statsMap = ladderStatsDAO.findStats
        (
            Set.of(Region.EU),
            new HashSet<>(Arrays.asList(BaseLeague.LeagueType.values())),
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        )
            .get(SeasonGenerator.DEFAULT_SEASON_ID);
        assertEquals(5, statsMap.getRaceTeamCount().get(Race.TERRAN));
        assertEquals(8, statsMap.getRaceTeamCount().get(Race.PROTOSS));
        assertEquals(10, statsMap.getRaceTeamCount().get(Race.ZERG));
        assertEquals(47, statsMap.getRaceTeamCount().get(Race.RANDOM));
    }

    private int setLegacyIdRace(Region region, int from, int toExcluding, Race race)
    {

        return jdbcTemplate.update
        (
            "WITH region_team AS "
            + "("
                + "SELECT id "
                + "FROM team "
                + "WHERE region = " + conversionService.convert(region, Integer.class) + " "
                + "ORDER BY id "
                + "LIMIT " + (toExcluding - from) + " OFFSET " + (from - 1) + " "
            + ")"
            + "UPDATE team "
            + "SET legacy_id = (legacy_id::text || '"
                + conversionService.convert(race, Integer.class) + "')::bigint "
            + "FROM region_team "
            + "WHERE team.id = region_team.id"
        );
    }


}
