// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class CharacterSearchIT
{

    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @BeforeAll
    public static void beforeAll(@Autowired DataSource dataSource)
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
    public void testStatsCalculation()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 1L, region, 2020, 1);
        //generate some noise
        seasonGenerator.generateSeason(List.of(season1),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            5
        );

        //create what matters
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Account acc = accountDAO.create(new Account(null, "refaccount#123"));
        PlayerCharacter character1 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9998L, 1, "refchar1#123"));
        PlayerCharacter character2 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9999L, 1, "refchar2#123"));
        Team team1 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            bronze1.getId(), BigInteger.valueOf(11111L), 100L,
            100, 0, 0, 0
        );
        teamDAO.create(team1);
        TeamMember member1 = new TeamMember
        (
            team1.getId(), character1.getId(),
            100, 0, 0, 0
        );
        teamMemberDAO.create(member1);
        Team team2 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            bronze1.getId(), BigInteger.valueOf(11112L), 101L,
            100, 0, 0, 0
        );
        teamDAO.create(team2);
        TeamMember member2 = new TeamMember
        (
            team2.getId(), character2.getId(),
            0, 100, 0, 0
        );
        teamMemberDAO.create(member2);
        playerCharacterStatsDAO.mergeCalculate(season1.getBattlenetId());
        playerCharacterStatsDAO.mergeCalculateGlobal();

        List<LadderDistinctCharacter> byName = ladderCharacterDAO.findDistinctCharactersByName("refchar1");
        assertEquals(1, byName.size());
        LadderDistinctCharacter char1 = byName.get(0);
        assertEquals("refchar1#123", char1.getMembers().getCharacter().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char1.getLeagueMax());
        assertEquals(100, char1.getRatingMax());
        assertEquals(100, char1.getTotalGamesPlayed());

        List<LadderDistinctCharacter> byAccount = ladderCharacterDAO.findDistinctCharactersByAccountId(acc.getId());
        assertEquals(2, byAccount.size());
        LadderDistinctCharacter char11 = byAccount.get(1);
        assertEquals("refchar1#123", char11.getMembers().getCharacter().getName());
        //sorted by rating max
        LadderDistinctCharacter char12 = byAccount.get(0);
        assertEquals("refchar2#123", char12.getMembers().getCharacter().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char1.getLeagueMax());
        assertEquals(101, char12.getRatingMax());
        assertEquals(100, char12.getTotalGamesPlayed());
    }

}
