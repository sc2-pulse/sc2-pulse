// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
@AutoConfigureMockMvc
public class PlayerCharacterStatsIT
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    throws Exception
    {
        PlayerCharacter character = setupStats();
        playerCharacterStatsDAO.calculate();
        playerCharacterStatsDAO.mergeCalculate(); //just for testing, not actually required
        List<LadderPlayerCharacterStats> stats = WebServiceTestUtil.getObject
        (
            mvc,
            objectMapper,
            new TypeReference<>(){},
            "/api/character/{id}/stats/full",
            character.getId()
        );
        verifyStats(character, LadderPlayerCharacterStatsDAO.transform(stats));
    }

    @Test
    public void testRecentStatsCalculation()
    throws Exception
    {
        PlayerCharacter character = setupStats();
        playerCharacterStatsDAO.calculate(SC2Pulse.offsetDateTime().minusHours(1));
        playerCharacterStatsDAO.mergeCalculate(SC2Pulse.offsetDateTime().minusHours(1)); //just for testing, not actually required
        List<LadderPlayerCharacterStats> stats = WebServiceTestUtil.getObject
        (
            mvc,
            objectMapper,
            new TypeReference<>(){},
            "/api/character/{id}/stats/full",
            character.getId()
        );
        verifyStats(character, LadderPlayerCharacterStatsDAO.transform(stats));
    }

    @Test
    public void testStatsCalculationById()
    throws Exception
    {
        PlayerCharacter character = setupStats();
        playerCharacterStatsDAO.mergeCalculate(Set.of(character.getId(), 99912345L));
        List<LadderPlayerCharacterStats> stats = WebServiceTestUtil.getObject
        (
            mvc,
            objectMapper,
            new TypeReference<>(){},
            "/api/character/{id}/stats/full",
            character.getId()
        );
        verifyStats(character, LadderPlayerCharacterStatsDAO.transform(stats));
    }

    public PlayerCharacter setupStats()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 1, region, 2020, 1,
            SC2Pulse.offsetDateTime(2020, 1, 1), SC2Pulse.offsetDateTime(2020, 2, 1));
        Season season2 = new Season(null, 2, region, 2020, 2,
            SC2Pulse.offsetDateTime(2020, 2, 1), SC2Pulse.offsetDateTime(2020, 3, 1));
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
        int depth = QUEUE_TYPE == QueueType.LOTV_1V1 ? teamStateDAO.getMaxDepthDaysMain() : teamStateDAO.getMaxDepthDaysSecondary();
        teamStateDAO.archive(SC2Pulse.offsetDateTime().minusDays(depth + 2));
        teamStateDAO.cleanArchive(SC2Pulse.offsetDateTime().minusDays(depth + 2));
        teamStateDAO.removeExpired();
        return character;
    }

    private void verifyStats(PlayerCharacter character, Map<QueueType, Map<TeamType, Map<Race, LadderPlayerCharacterStats>>> stats)
    {
        assertNull(stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.RANDOM));

        LadderPlayerCharacterStats terranStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.TERRAN);
        verifyStats(terranStats, character, Race.TERRAN, BaseLeague.LeagueType.BRONZE, 2L, 97, 1, 97, null, null);

        LadderPlayerCharacterStats protossStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.PROTOSS);
        verifyStats(protossStats, character, Race.PROTOSS, BaseLeague.LeagueType.BRONZE, 2L, 97, 1, 97, null, null);

        LadderPlayerCharacterStats zergStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(Race.ZERG);
        verifyStats(zergStats, character, Race.ZERG, BaseLeague.LeagueType.DIAMOND, 4L, 291, 3, 194, 2, 97);

        LadderPlayerCharacterStats globalStats = stats.get(QUEUE_TYPE).get(TEAM_TYPE).get(null);
        verifyStats(globalStats, character, null, BaseLeague.LeagueType.DIAMOND, 4L, 600, 3, 400, 2, 200);
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
            100, 0, 0, 0,
            SC2Pulse.offsetDateTime()
        );
        teamDAO.create(team);
        teamStateDAO.saveState(Set.of(TeamState.of(team)));
        TeamState maxState = TeamState.of(team, SC2Pulse.offsetDateTime().minusDays(
            (queueType == QueueType.LOTV_1V1 ? teamStateDAO.getMaxDepthDaysMain() : teamStateDAO.getMaxDepthDaysSecondary()) + 1));
        maxState.setRating((int) (team.getRating() + 1));
        teamStateDAO.saveState(Set.of(maxState));
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
                null, null, null, null
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
        Integer ratingPrev,
        Integer gamesPrev,
        Integer ratingCur,
        Integer gamesPlayedCur
    )
    {
        assertEquals(character.getId(), stats.getStats().getPlayerCharacterId());
        assertEquals(race, stats.getStats().getRace());
        assertEquals(leagueMax, stats.getStats().getLeagueMax());
        assertEquals(ratingMax, (long) stats.getStats().getRatingMax());
        assertEquals(gamesPlayed, (long) stats.getStats().getGamesPlayed());
        assertEquals(ratingPrev, stats.getPreviousStats().getRating());
        assertEquals(gamesPrev, stats.getPreviousStats().getGamesPlayed());
        assertEquals(ratingCur, stats.getCurrentStats().getRating());
        assertEquals(gamesPlayedCur, stats.getCurrentStats().getGamesPlayed());
    }

}
