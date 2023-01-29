// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
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
public class LadderCharacterTeamSearchIT
{

    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;


    private static PlayerCharacter[] characters;
    private static Team team1, team2, team3, team4;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DivisionDAO divisionDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }

        Region region = Region.EU;
        Season season1 = new Season(null, 1, region, 2020, 1,
            LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1));
        Season season2 = new Season(null, 2, region, 2020, 2,
            LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1));
        //generate some useless noise
        seasonGenerator.generateSeason
        (
            List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_4V4),
            TEAM_TYPE,
            TIER_TYPE,
            3
        );
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE, TIER_TYPE).get(0);
        Division gold2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.GOLD, QueueType.LOTV_4V4, TEAM_TYPE, TIER_TYPE).get(0);
        Division bronze1v1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE, TIER_TYPE).get(0);

        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "refacc", 4);
        characters = seasonGenerator.generateCharacters("refchar", accounts, region, 10000);

        team1 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, bronze1,
            BigInteger.valueOf(10002L), 1L, 1, 2, 3, 4, characters
        );
        team2 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, bronze1,
            BigInteger.valueOf(10000L), 2L, 1, 2, 3, 4, characters
        );
        team3 = seasonGenerator.createTeam
        (
            season2, new BaseLeague(BaseLeague.LeagueType.GOLD, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, gold2,
            BigInteger.valueOf(10001L), 3L, 1, 2, 3, 4, characters
        );
        team4 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, bronze1v1,
            BigInteger.valueOf(10003L), 0L, 1, 2, 3, 4, characters[0]
        );
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
    public void testFindCharacterTeams()
    {
        List<LadderTeam> teams = ladderSearchDAO.findCharacterTeams(characters[0].getId());
        assertEquals(4, teams.size());

        // order by team.season DESC
        // team.queue_type ASC, team.team_type ASC, team.league_type DESC
        // team.rating DESC, team.id ASC

        assertEquals(team3.getId(), teams.get(0).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(0).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));

        assertEquals(team4.getId(), teams.get(1).getId());
        assertEquals(teams.get(1).getMembers().get(0).getCharacter(), characters[0]);

        assertEquals(team2.getId(), teams.get(2).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(2).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));

        assertEquals(team1.getId(), teams.get(3).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(3).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));
    }

    @Test
    public void testFindCharacterTeamsSeasonFilter()
    {
        List<LadderTeam> teams = ladderSearchDAO
            .findCharacterTeams(characters[0].getId(), Set.of(1), Set.of(), null);
        assertEquals(3, teams.size());

        // order by team.season DESC
        // team.queue_type ASC, team.team_type ASC, team.league_type DESC
        // team.rating DESC, team.id ASC

        assertEquals(team4.getId(), teams.get(0).getId());
        assertEquals(teams.get(0).getMembers().get(0).getCharacter(), characters[0]);

        assertEquals(team2.getId(), teams.get(1).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(1).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));

        assertEquals(team1.getId(), teams.get(2).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(2).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));
    }

    @Test
    public void testFindCharacterTeamsQueueTypeFilter()
    {
        List<LadderTeam> teams = ladderSearchDAO
            .findCharacterTeams(characters[0].getId(), Set.of(), Set.of(QueueType.LOTV_4V4), null);
        assertEquals(3, teams.size());

        // order by team.season DESC
        // team.queue_type ASC, team.team_type ASC, team.league_type DESC
        // team.rating DESC, team.id ASC

        assertEquals(team3.getId(), teams.get(0).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(0).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));

        assertEquals(team2.getId(), teams.get(1).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(1).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));

        assertEquals(team1.getId(), teams.get(2).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(2).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));
    }

    @Test
    public void testFindCharacterTeamsLimit()
    {
        List<LadderTeam> teams = ladderSearchDAO
            .findCharacterTeams(characters[0].getId(), Set.of(), Set.of(), 1);
        assertEquals(1, teams.size());

        assertEquals(team3.getId(), teams.get(0).getId());
        for(PlayerCharacter character : characters)
            assertTrue(teams.get(0).getMembers().stream().anyMatch(m->m.getCharacter().equals(character)));
    }

}
