package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringJUnitConfig(classes = Application.class)
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
        Season season2 = new Season(null, 2L, region, 2020, 2);
        //generate some useless noise
        seasonGenerator.generateSeason
        (
            List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            QUEUE_TYPE,
            TEAM_TYPE,
            TIER_TYPE,
            5
        );
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Division diamond1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Division gold2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.GOLD, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);

        //create ref data that actually matters
        Account acc = accountDAO.create(new Account(null, region, 9999L, "refaccount#123"));
        PlayerCharacter character = playerCharacterDAO.create(new PlayerCharacter(null, acc.getId(), 9999L, 1, "refchar#123"));
        createTeam(season1, Race.TERRAN, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(9999L), 1L, character);
        createTeam(season1, Race.PROTOSS, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(10000L), 1L, character);
        createTeam(season1, Race.ZERG, region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, bronze1, BigInteger.valueOf(10001L), 1L, character);
        createTeam(season1, Race.ZERG, region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, diamond1, BigInteger.valueOf(10002L), 3L, character);
        createTeam(season2, Race.ZERG, region, BaseLeague.LeagueType.GOLD, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, gold2, BigInteger.valueOf(10003L), 2L, character);
        createTeam(season2, null, region, BaseLeague.LeagueType.DIAMOND, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, diamond1, BigInteger.valueOf(10004L), 2L, character);
        playerCharacterStatsDAO.calculate(season1.getBattlenetId());
        playerCharacterStatsDAO.mergeCalculate(season1.getBattlenetId()); //just for testing, not actually required
        playerCharacterStatsDAO.calculate(season2.getBattlenetId());
        playerCharacterStatsDAO.mergeCalculate(season2.getBattlenetId()); //just for testing, not actually required
        playerCharacterStatsDAO.calculateGlobal();
        playerCharacterStatsDAO.mergeCalculateGlobal(); //just for testing, not actually required
        Map<QueueType, Map<TeamType, Map<Race, PlayerCharacterStats>>> stats =
            playerCharacterStatsDAO.findGlobalMap(character.getId());

        assertNull(stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.RANDOM));

        PlayerCharacterStats terranStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.TERRAN);
        verifyStats(terranStats, character, Race.TERRAN, BaseLeague.LeagueType.BRONZE, 1L, 97);

        PlayerCharacterStats protossStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.PROTOSS);
        verifyStats(protossStats, character, Race.PROTOSS, BaseLeague.LeagueType.BRONZE, 1L, 97);

        PlayerCharacterStats zergStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.ZERG);
        verifyStats(zergStats, character, Race.ZERG, BaseLeague.LeagueType.DIAMOND, 3L, 291);

        PlayerCharacterStats globalStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(null);
        verifyStats(globalStats, character, null, BaseLeague.LeagueType.DIAMOND, 3L, 595);

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
        BigInteger battlenetId,
        long rating,
        PlayerCharacter character
    )
    {
        Team team = new Team
        (
            null, season.getBattlenetId(), region,
            new BaseLeague(league, queueType, teamType), tierType,
            division.getId(),
            battlenetId,
            rating,
            100, 0, 0, 0
        );
        teamDAO.create(team);
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
        PlayerCharacterStats stats,
        PlayerCharacter character,
        Race race,
        BaseLeague.LeagueType leagueMax,
        long ratingMax,
        int gamesPlayed
    )
    {
        assertEquals(character.getId(), stats.getPlayerCharacterId());
        assertEquals(race, stats.getRace());
        assertEquals(leagueMax, stats.getLeagueMax());
        assertEquals(ratingMax, (long) stats.getRatingMax());
        assertEquals(gamesPlayed, (long) stats.getGamesPlayed());
    }

}
