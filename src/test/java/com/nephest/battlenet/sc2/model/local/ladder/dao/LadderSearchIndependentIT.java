// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderSearchIndependentIT
{

    public static final QueueType QUEUE_TYPE = LadderCharacterDAO.CURRENT_STATS_QUEUE_TYPE;
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

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private ClanDAO clanDAO;

    @BeforeEach
    public void beforeAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterAll(@Autowired DataSource dataSource)
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
        Season season1 = new Season(null, 1, region, 2020, 1,
            LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1));
        Season season2 = new Season(null, 2, region, 2020, 2,
            LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1));
        //generate some noise
        seasonGenerator.generateSeason(List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            5
        );

        //create a pro player who has 2 accounts. First account has 2 characters, second account has one character
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Division bronze2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region,
            BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        Account acc = accountDAO.create(new Account(null, Partition.GLOBAL, "refaccount#123"));
        Account acc2 = accountDAO.create(new Account(null, Partition.GLOBAL, "refaccount2#123"));
        ProPlayer proPlayer = new ProPlayer(null, new byte[]{0x1, 0x2}, "refnickname", "pro name");
        proPlayerDAO.merge(proPlayer);
        proPlayerAccountDAO.link(proPlayer.getId(), acc.getBattleTag(), acc2.getBattleTag());
        Clan clan = clanDAO.merge(new Clan(null, "clanTag", Region.EU, "clanName"))[0];
        PlayerCharacter character1 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9998L, 1, "refchar1#123", clan.getId()));
        PlayerCharacter character2 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9999L, 1, "refchar2#123", clan.getId()));
        PlayerCharacter character3 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc2.getId(), region, 9997L, 1, "refchar3#123"));
        Team team1 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11111L), bronze1.getId(),
            100L, 100, 0, 0, 0
        );
        teamDAO.create(team1);
        TeamMember member1 = new TeamMember
        (
            team1.getId(), character1.getId(),
            100, 0, 0, 0
        );
        teamMemberDAO.create(member1);
        Team team1_2 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11114L), bronze2.getId(),
            98L, 99, 0, 0, 0
        );
        teamDAO.create(team1_2);
        TeamMember member1_2 = new TeamMember
        (
            team1_2.getId(), character1.getId(),
            99, 0, 0, 0
        );
        teamMemberDAO.create(member1_2);
        Team team1_3 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11115L), bronze2.getId(),
            97L, 50, 0, 0, 0
        );
        teamDAO.create(team1_3);
        TeamMember member1_3 = new TeamMember
        (
            team1_3.getId(), character1.getId(),
            0, 50, 0, 0
        );
        teamMemberDAO.create(member1_3);
        Team team2 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11112L), bronze1.getId(),
            101L, 100, 0, 0, 0
        );
        teamDAO.create(team2);
        TeamMember member2 = new TeamMember
        (
            team2.getId(), character2.getId(),
            0, 100, 0, 0
        );
        teamMemberDAO.create(member2);
        Team team3 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11113L), bronze1.getId(),
            102L, 100, 0, 0, 0
        );
        teamDAO.create(team3);
        TeamMember member3 = new TeamMember
        (
            team3.getId(), character3.getId(),
            0, 0, 100, 0
        );
        teamMemberDAO.create(member3);

        Team team3_2 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(11113L), bronze1.getId(),
            102L, 100, 0, 0, 0
        );
        teamDAO.create(team3_2);
        TeamMember member3_2 = new TeamMember
        (
            team3_2.getId(), character3.getId(),
            0, 0, 100, 0
        );
        teamMemberDAO.create(member3_2);
        playerCharacterStatsDAO.mergeCalculate();

        List<LadderDistinctCharacter> byName = ladderCharacterDAO.findDistinctCharacters("refchar1");
        assertEquals(1, byName.size());
        LadderDistinctCharacter char1 = byName.get(0);
        assertEquals("refchar1#123", char1.getMembers().getCharacter().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char1.getLeagueMax());
        assertEquals(100, char1.getRatingMax());
        assertEquals(199, char1.getTotalGamesPlayed());
        assertEquals(98, char1.getRatingCurrent());
        assertEquals(149, char1.getGamesPlayedCurrent());
        assertEquals(2147483647, char1.getRankCurrent());

        List<LadderDistinctCharacter> byAccount = ladderCharacterDAO.findDistinctCharactersByAccountId(acc.getId());
        verifyCharacterAccountStats(byAccount);
        List<LadderDistinctCharacter> byAccountName = ladderCharacterDAO.findDistinctCharacters("refaccount");
        verifyCharacterAccountStats(byAccountName);
        List<LadderDistinctCharacter> byProNickname = ladderCharacterDAO.findDistinctCharacters("refnickname");
        verifyProCharacterAccountStats(byProNickname);
        List<LadderDistinctCharacter> byFullAccountName = ladderCharacterDAO.findDistinctCharacters("refaccount#123");
        verifyCharacterAccountStats(byFullAccountName);
        List<LadderDistinctCharacter> byClanTag = ladderCharacterDAO.findDistinctCharacters("[clanTag]");
        verifyCharacterAccountStats(byClanTag);
        LadderDistinctCharacter byProfileLink = ladderCharacterDAO
            .findDistinctCharacterByProfileLink("https://starcraft2.com/profile/2/1/9998")
            .orElse(null);
        assertNotNull(byProfileLink);
        assertEquals("refaccount#123", byProfileLink.getMembers().getAccount().getBattleTag());
        assertEquals("refchar1#123", byProfileLink.getMembers().getCharacter().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, byProfileLink.getLeagueMax());
        assertEquals(100, byProfileLink.getRatingMax());
        assertEquals(199, byProfileLink.getTotalGamesPlayed());
        assertEquals(98, byProfileLink.getRatingCurrent());
        assertEquals(149, byProfileLink.getGamesPlayedCurrent());
        assertEquals(2147483647, byProfileLink.getRankCurrent());

        //sorted asc
        List<Long> proCharacterIds = playerCharacterDAO.findProPlayerCharacterIds();
        assertEquals(3, proCharacterIds.size());
        assertEquals(character1.getId(), proCharacterIds.get(0));
        assertEquals(character2.getId(), proCharacterIds.get(1));
        assertEquals(character3.getId(), proCharacterIds.get(2));
        List<PlayerCharacter> proCharacters = playerCharacterDAO.findProPlayerCharacters();
        assertEquals(3, proCharacterIds.size());
        assertEquals(character1, proCharacters.get(0));
        assertEquals(character2, proCharacters.get(1));
        assertEquals(character3, proCharacters.get(2));

        //all chars of the same pro player are linked
        List<LadderDistinctCharacter> linked =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(character3.getId());
        verifyProCharacterAccountStats(linked);
        List<LadderDistinctCharacter> linkedByAccount =
            ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(acc2.getId());
        verifyProCharacterAccountStats(linkedByAccount);
        //all chars of the same account are linked
        proPlayerAccountDAO.unlink(proPlayer.getId(), acc.getId());
        proPlayerAccountDAO.unlink(proPlayer.getId(), acc2.getId());
        List<LadderDistinctCharacter> linkedNoPro =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(character1.getId());
        verifyCharacterAccountStats(linkedNoPro);
        List<LadderDistinctCharacter> linkedByAccountNoPro =
            ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(acc.getId());
        verifyCharacterAccountStats(linkedByAccountNoPro);
    }

    private void verifyCharacterAccountStats(List<LadderDistinctCharacter> byAccount)
    {
        assertEquals(2, byAccount.size());
        //sorted by rating cur, rating max
        LadderDistinctCharacter char11 = byAccount.get(0);
        assertEquals("refaccount#123", char11.getMembers().getAccount().getBattleTag());
        assertEquals("refchar1#123", char11.getMembers().getCharacter().getName());
        assertNotNull(char11.getMembers().getClan());
        assertEquals("clanTag", char11.getMembers().getClan().getTag());
        assertEquals(Region.EU, char11.getMembers().getClan().getRegion());
        assertEquals("clanName", char11.getMembers().getClan().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char11.getLeagueMax());
        assertEquals(100, char11.getRatingMax());
        assertEquals(199, char11.getTotalGamesPlayed());
        assertEquals(98, char11.getRatingCurrent());
        assertEquals(149, char11.getGamesPlayedCurrent());
        assertEquals(2147483647, char11.getRankCurrent());

        LadderDistinctCharacter char12 = byAccount.get(1);
        assertEquals("refaccount#123", char12.getMembers().getAccount().getBattleTag());
        assertEquals("refchar2#123", char12.getMembers().getCharacter().getName());
        assertNotNull(char12.getMembers().getClan());
        assertEquals("clanTag", char12.getMembers().getClan().getTag());
        assertEquals(Region.EU, char12.getMembers().getClan().getRegion());
        assertEquals("clanName", char12.getMembers().getClan().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char12.getLeagueMax());
        assertEquals(101, char12.getRatingMax());
        assertEquals(100, char12.getTotalGamesPlayed());
        assertNull(char12.getRatingCurrent());
        assertNull(char12.getGamesPlayedCurrent());
        assertNull(char12.getRankCurrent());
    }

    private void verifyProCharacterAccountStats(List<LadderDistinctCharacter> byAccount)
    {
        assertEquals(3, byAccount.size());
        //sorted by rating max
        LadderDistinctCharacter char13 = byAccount.get(0);
        assertEquals("refaccount2#123", char13.getMembers().getAccount().getBattleTag());
        assertEquals("refchar3#123", char13.getMembers().getCharacter().getName());
        assertNull(char13.getMembers().getClan());
        assertEquals(BaseLeague.LeagueType.BRONZE, char13.getLeagueMax());
        assertEquals(102, char13.getRatingMax());
        assertEquals(200, char13.getTotalGamesPlayed());
        assertEquals(102, char13.getRatingCurrent());
        assertEquals(100, char13.getGamesPlayedCurrent());
        assertEquals(2147483647, char13.getRankCurrent());

        verifyCharacterAccountStats(List.of(byAccount.get(1), byAccount.get(2)));
    }

    @Test
    public void testFindCharacterTeams()
    {
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
        PlayerCharacter[] characters = seasonGenerator.generateCharacters("refchar", accounts, region, 10000);

        Team team1 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, bronze1,
            BigInteger.valueOf(10002L), 1L, 1, 2, 3, 4, characters
        );
        Team team2 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, bronze1,
            BigInteger.valueOf(10000L), 2L, 1, 2, 3, 4, characters
        );
        Team team3 = seasonGenerator.createTeam
        (
            season2, new BaseLeague(BaseLeague.LeagueType.GOLD, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, gold2,
            BigInteger.valueOf(10001L), 3L, 1, 2, 3, 4, characters
        );
        Team team4 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, bronze1v1,
            BigInteger.valueOf(10003L), 0L, 1, 2, 3, 4, characters[0]
        );

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

}
