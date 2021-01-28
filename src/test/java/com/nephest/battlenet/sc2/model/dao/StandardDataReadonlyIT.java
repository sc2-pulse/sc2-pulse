// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class StandardDataReadonlyIT
{

    @Autowired
    private DivisionDAO divisionDAO;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator generator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }

        generator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.values()),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            5
        );
    }

    @Test
    public void testDivisionFinders()
    {
        Map<Division, PlayerCharacter> ids = divisionDAO.findProfileDivisionIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        assertEquals(2, ids.size());
        List<Map.Entry<Division, PlayerCharacter>> orderedIds= ids.entrySet().stream()
            .sorted((e, e2)->e.getKey().getId().compareTo(e2.getKey().getId()))
            .collect(Collectors.toList());
        //diamond
        assertEquals(divisionDAO.findDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED, 4).get(),
            orderedIds.get(0).getKey());
        //master
        assertEquals(divisionDAO.findDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED, 5).get(),
            orderedIds.get(1).getKey());
        //grandmaster
        assertEquals(6, divisionDAO.findLastDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED).get());

        List<Long> longIds = divisionDAO.findDivisionIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        assertEquals(2, ids.size());
        longIds.sort(Comparator.naturalOrder());
        assertEquals(orderedIds.get(0).getKey().getBattlenetId(), longIds.get(0));
        assertEquals(orderedIds.get(1).getKey().getBattlenetId(), longIds.get(1));

        int count = divisionDAO.getDivisionCount
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        assertEquals(2, count);
    }

}
