// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterDAOIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testActiveCharacterFinder()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_2V2),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Division division = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_2V2, TeamType.ARRANGED, 10)
            .get();
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        PlayerCharacter char1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter char3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));
        Team team1 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("1"), division.getId(), 1L, 1, 1, 1, 1
        ));
        Team team2 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("2"), division.getId(), 2L, 2, 2, 2, 2
        ));
        teamMemberDAO.merge
        (
            new TeamMember(team1.getId(), char1.getId(), 1, 0, 0, 0),
            new TeamMember(team1.getId(), char2.getId(), 0, 1, 0, 0),
            new TeamMember(team2.getId(), char1.getId(), 0, 0, 1, 0),
            new TeamMember(team2.getId(), char3.getId(), 0, 0, 0, 1)
        );
        OffsetDateTime now = OffsetDateTime.now();
        TeamState state1 = TeamState.of(team1);
        state1.setDateTime(now.minusHours(1));
        TeamState state2 = TeamState.of(team2);
        state2.setDateTime(now.minusHours(2));
        teamStateDAO.saveState(state1, state2);

        assertTrue(playerCharacterDAO.findRecentlyActiveCharacters(now.minusMinutes(59)).isEmpty());

        List<PlayerCharacter> search1 = playerCharacterDAO.findRecentlyActiveCharacters(now.minusHours(1));
        search1.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(2, search1.size());
        assertEquals(char1, search1.get(0));
        assertEquals(char2, search1.get(1));

        List<PlayerCharacter> search2 = playerCharacterDAO.findRecentlyActiveCharacters(now.minusHours(2));
        search2.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(3, search2.size());
        assertEquals(char1, search2.get(0));
        assertEquals(char2, search2.get(1));
        assertEquals(char3, search2.get(2));
    }

}
