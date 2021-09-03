// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterStatsDAO;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterStatsDAOIT
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
    private LadderPlayerCharacterStatsDAO ladderPlayerCharacterStatsDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

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
    public void testGlobalStatsCalculation()
    {
        PlayerCharacter character = setupStats();
        playerCharacterStatsDAO.calculate();
        playerCharacterStatsDAO.mergeCalculate(); //just for testing, not actually required
        Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> stats =
            ladderPlayerCharacterStatsDAO.findGlobalMap(character.getId());
        verifyStats(character, stats);
    }

    @Test
    public void testRecentStatsCalculation()
    {
        PlayerCharacter character = setupStats();
        playerCharacterStatsDAO.calculate(OffsetDateTime.now().minusHours(1));
        playerCharacterStatsDAO.mergeCalculate(OffsetDateTime.now().minusHours(1)); //just for testing, not actually required
        Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> stats =
            ladderPlayerCharacterStatsDAO.findGlobalMap(character.getId());
        verifyStats(character, stats);
    }

    public PlayerCharacter setupStats()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 1, region, 2020, 1, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1));
        Season season2 = new Season(null, 2, region, 2020, 2, LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1));
        //generate some useless noise
        seasonGenerator.generateSeason
        (
            List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            5
        );
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Division diamond1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Division gold2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.GOLD, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);

        //create ref data that actually matters
        Account acc = accountDAO.create(new Account(null, Partition.GLOBAL, "refaccount#123"));
        PlayerCharacter character = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9999L, 1, "refchar#123"));
        createTeam(season1, Race.TERRAN, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(9999L), 1L, character);
        createTeam(season1, Race.PROTOSS, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(10000L), 1L, character);
        createTeam(season1, Race.ZERG, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(10001L), 1L, character);
        createTeam(season1, Race.ZERG, region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, diamond1, BigInteger.valueOf(10002L), 3L, character);
        createTeam(season2, Race.ZERG, region, BaseLeague.LeagueType.GOLD, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, gold2, BigInteger.valueOf(10003L), 2L, character);
        createTeam(season2, null, region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, diamond1, BigInteger.valueOf(10004L), 2L, character);
        int depth = QUEUE_TYPE == QueueType.LOTV_1V1 ? TeamStateDAO.getMaxDepthDaysMain() : TeamStateDAO.getMaxDepthDaysSecondary();
        teamStateDAO.archive(OffsetDateTime.now().minusDays(depth + 2));
        teamStateDAO.cleanArchive(OffsetDateTime.now().minusDays(depth + 2));
        teamStateDAO.removeExpired();
        return character;
    }

    private void verifyStats(PlayerCharacter character, Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> stats)
    {
        assertNull(stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.RANDOM));

        LadderPlayerCharacterStats terranStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.TERRAN);
        verifyStats(terranStats, character, Race.TERRAN, BaseLeague.LeagueType.BRONZE, 2L, 97, null, null);

        LadderPlayerCharacterStats protossStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.PROTOSS);
        verifyStats(protossStats, character, Race.PROTOSS, BaseLeague.LeagueType.BRONZE, 2L, 97, null, null);

        LadderPlayerCharacterStats zergStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.ZERG);
        verifyStats(zergStats, character, Race.ZERG, BaseLeague.LeagueType.DIAMOND, 4L, 291, 2, 97);

        LadderPlayerCharacterStats globalStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(null);
        verifyStats(globalStats, character, null, BaseLeague.LeagueType.DIAMOND, 4L, 595, 2, 199);
    }

    private void createTeam
    (
        Season season,
        Race race,
        Region region,
        BaseLeague.LeagueType league,
        QueueType queueType,
        TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType,
        Division division,
        BigInteger legacyId,
        long rating,
        PlayerCharacter character
    )
    {
        Team team = new Team
        (
            null,
            season.getBattlenetId(), region,
            new BaseLeague(league, queueType, teamType), tierType,
            legacyId,
            division.getId(),
            rating,
            100, 0, 0, 0
        );
        teamDAO.create(team);
        teamStateDAO.saveState(TeamState.of(team));
        TeamState maxState = TeamState.of(team, OffsetDateTime.now().minusDays(
            (queueType == QueueType.LOTV_1V1 ? TeamStateDAO.getMaxDepthDaysMain() : TeamStateDAO.getMaxDepthDaysSecondary()) + 1));
        maxState.setRating((int) (team.getRating() + 1));
        teamStateDAO.saveState(maxState);
        TeamMember member;
        if (race != null)
        {
            //only a team where a player played more than 90% of games with some particular race matters for racial stats
            member = new TeamMember
            (
                team.getId(), character.getId(),
                race == Race.TERRAN ? 97 : 1,
                race == Race.PROTOSS ? 97 : 1,
                race == Race.ZERG ? 97 : 1,
                race == Race.RANDOM ? 97 : null
            );
        }
        else
        {
            //this data should not be included into the racial stats, but it must be included into the global stats
            member = new TeamMember
            (
                team.getId(), character.getId(),
                25, 25, 25, 25
            );
        }
        teamMemberDAO.create(member);
    }

    private void verifyStats
    (
        LadderPlayerCharacterStats stats,
        PlayerCharacter character,
        Race race,
        BaseLeague.LeagueType leagueMax,
        long ratingMax,
        int gamesPlayed,
        Integer ratingCur,
        Integer gamesPlayedCur
    )
    {
        assertEquals(character.getId(), stats.getStats().getPlayerCharacterId());
        assertEquals(race, stats.getStats().getRace());
        assertEquals(leagueMax, stats.getStats().getLeagueMax());
        assertEquals(ratingMax, (long) stats.getStats().getRatingMax());
        assertEquals(gamesPlayed, (long) stats.getStats().getGamesPlayed());
        assertEquals(ratingCur, stats.getRatingCurrent());
        assertEquals(gamesPlayedCur, stats.getGamesPlayedCurrent());
    }

}
