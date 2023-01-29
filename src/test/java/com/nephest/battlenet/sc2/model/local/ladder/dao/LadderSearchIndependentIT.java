// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
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

    @Autowired
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMvc mvc;

    private static final String BATTLETAG = "refaccount123#123";

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired CacheManager cacheManager,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
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
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = BATTLETAG)
    public void testStatsCalculation()
    throws Exception
    {
        accountDAO.merge(new Account(null, Partition.GLOBAL, BATTLETAG));
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
        //the names should be updated
        proPlayerDAO.merge(new ProPlayer(null, new byte[]{0x1, 0x2}, "refnickname_remove", "pro name_remove"));
        ProPlayer proPlayer = new ProPlayer(null, new byte[]{0x1, 0x2}, "refnickname", "pro name");
        proPlayerDAO.merge(proPlayer);
        proPlayerAccountDAO.link(proPlayer.getId(), acc.getBattleTag(), acc2.getBattleTag());
        Clan clan = clanDAO.merge(new Clan(null, "clanTag", Region.EU, "clanName"))[0];
        PlayerCharacter character1 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9998L, 1, "refchar1#123"));
        PlayerCharacter character2 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc.getId(), region, 9999L, 1, "refchar2#123"));
        PlayerCharacter character3 = playerCharacterDAO
            .create(new PlayerCharacter(null, acc2.getId(), region, 9997L, 1, "refchar3#123"));
        clanMemberDAO.merge
        (
            new ClanMember(character1.getId(), clan.getId()),
            new ClanMember(character2.getId(), clan.getId())
        );
        Team team1 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.SILVER, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
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
        assertEquals(BaseLeague.LeagueType.SILVER, char1.getLeagueMax());
        assertEquals(100, char1.getRatingMax());
        assertEquals(249, char1.getTotalGamesPlayed());
        assertEquals(98, char1.getCurrentStats().getRating());
        assertEquals(149, char1.getCurrentStats().getGamesPlayed());
        assertNull(char1.getCurrentStats().getRank());
        assertEquals(100, char1.getPreviousStats().getRating());
        assertEquals(100, char1.getPreviousStats().getGamesPlayed());
        assertNull(char1.getPreviousStats().getRank());

        List<LadderDistinctCharacter> byAccount = ladderCharacterDAO.findDistinctCharactersByAccountId(acc.getId());
        verifyCharacterAccountStats(byAccount);
        List<LadderDistinctCharacter> byAccountName = ladderCharacterDAO.findDistinctCharacters("refaccount");
        verifyCharacterAccountStats(byAccountName);
        List<LadderDistinctCharacter> byProNickname = ladderCharacterDAO.findDistinctCharacters("refnickname");
        verifyProCharacterAccountStats(byProNickname);
        List<LadderDistinctCharacter> byFullAccountName = ladderCharacterDAO.findDistinctCharacters("rEfaccount#123");
        verifyCharacterAccountStats(byFullAccountName);
        List<LadderDistinctCharacter> byClanTag = ladderCharacterDAO.findDistinctCharacters("[clanTag]");
        verifyCharacterAccountStats(byClanTag);
        LadderDistinctCharacter byProfileLink = ladderCharacterDAO
            .findDistinctCharacterByProfileLink("https://starcraft2.com/profile/2/1/9998")
            .orElse(null);
        assertNotNull(byProfileLink);
        assertEquals("refaccount#123", byProfileLink.getMembers().getAccount().getBattleTag());
        assertEquals("refchar1#123", byProfileLink.getMembers().getCharacter().getName());
        assertEquals(BaseLeague.LeagueType.SILVER, byProfileLink.getLeagueMax());
        assertEquals(100, byProfileLink.getRatingMax());
        assertEquals(249, byProfileLink.getTotalGamesPlayed());
        assertEquals(98, byProfileLink.getCurrentStats().getRating());
        assertEquals(149, byProfileLink.getCurrentStats().getGamesPlayed());
        assertNull(byProfileLink.getCurrentStats().getRank());
        assertEquals(100, byProfileLink.getPreviousStats().getRating());
        assertEquals(100, byProfileLink.getPreviousStats().getGamesPlayed());
        assertNull(byProfileLink.getPreviousStats().getRank());

        mvc.perform
        (
            get("/api/my/following/characters")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andExpect(content().json("[]"))
        .andReturn();

        mvc.perform
        (
            post("/api/my/following/" + acc.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/my/following/" + acc2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        List<LadderDistinctCharacter> byFollowing = Arrays.stream(objectMapper.readValue(mvc.perform(get(
            "/api/my/following/characters").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(), LadderDistinctCharacter[].class)).collect(Collectors.toList());
        verifyProCharacterAccountStats(byFollowing);

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
        LadderDistinctCharacter char11 = byAccount.get(1);
        assertEquals("refaccount#123", char11.getMembers().getAccount().getBattleTag());
        assertEquals("refchar1#123", char11.getMembers().getCharacter().getName());
        assertNotNull(char11.getMembers().getClan());
        assertEquals("clanTag", char11.getMembers().getClan().getTag());
        assertEquals(Region.EU, char11.getMembers().getClan().getRegion());
        assertEquals("clanName", char11.getMembers().getClan().getName());
        assertEquals(BaseLeague.LeagueType.SILVER, char11.getLeagueMax());
        assertEquals(100, char11.getRatingMax());
        assertEquals(249, char11.getTotalGamesPlayed());
        assertEquals(98, char11.getCurrentStats().getRating());
        assertEquals(149, char11.getCurrentStats().getGamesPlayed());
        assertNull(char11.getCurrentStats().getRank());
        assertEquals(100, char11.getPreviousStats().getRating());
        assertEquals(100, char11.getPreviousStats().getGamesPlayed());
        assertNull(char11.getPreviousStats().getRank());

        LadderDistinctCharacter char12 = byAccount.get(0);
        assertEquals("refaccount#123", char12.getMembers().getAccount().getBattleTag());
        assertEquals("refchar2#123", char12.getMembers().getCharacter().getName());
        assertNotNull(char12.getMembers().getClan());
        assertEquals("clanTag", char12.getMembers().getClan().getTag());
        assertEquals(Region.EU, char12.getMembers().getClan().getRegion());
        assertEquals("clanName", char12.getMembers().getClan().getName());
        assertEquals(BaseLeague.LeagueType.BRONZE, char12.getLeagueMax());
        assertEquals(101, char12.getRatingMax());
        assertEquals(100, char12.getTotalGamesPlayed());
        assertNull(char12.getCurrentStats().getRating());
        assertNull(char12.getCurrentStats().getGamesPlayed());
        assertNull(char12.getCurrentStats().getRank());
        assertEquals(101, char12.getPreviousStats().getRating());
        assertEquals(100, char12.getPreviousStats().getGamesPlayed());
        assertNull(char12.getPreviousStats().getRank());
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
        assertEquals(102, char13.getCurrentStats().getRating());
        assertEquals(100, char13.getCurrentStats().getGamesPlayed());
        assertNull(char13.getCurrentStats().getRank());
        assertEquals(102, char13.getPreviousStats().getRating());
        assertEquals(100, char13.getPreviousStats().getGamesPlayed());
        assertNull(char13.getPreviousStats().getRank());

        verifyCharacterAccountStats(List.of(byAccount.get(1), byAccount.get(2)));
    }

    @Test
    public void testRaceIdentification()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 1, region, 2020, 1,
            LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1));
        Season season2 = new Season(null, 2, region, 2020, 2,
            LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1));
        Season season3 = new Season(null, 3, region, 2020, 3,
            LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 1));

        seasonGenerator.generateSeason
        (
            List.of(season1, season2, season3),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_4V4),
            TEAM_TYPE,
            TIER_TYPE,
            0
        );

        Division d1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE, TIER_TYPE).get(0);
        Division d2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE, TIER_TYPE).get(0);
        Division d3 = divisionDAO.findListByLadder(season3.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE, TIER_TYPE).get(0);

        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "btag#1"));
        PlayerCharacter character = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter[] characters = new PlayerCharacter[]{character};

        //top mmr, but old season
        Team team1 = new Team
        (
            null, season1.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(10001L), d1.getId(),
            3L, 100, 0, 0, 0
        );
        //2nd mmr, but prev season
        Team team2 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(10002L), d2.getId(),
            2L, 100, 0, 0, 0
        );
        //3rd mmr, picked because it's the latest team
        Team team3 = new Team
        (
            null, season3.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE,
            BigInteger.valueOf(10003L), d3.getId(),
            1L, 100, 0, 0, 0
        );
        teamDAO.merge(team1, team2, team3);
        teamMemberDAO.merge
        (
            new TeamMember(team1.getId(), character.getId(), 100, 0, 0, 0),
            new TeamMember(team2.getId(), character.getId(), 0, 100, 0, 0),
            new TeamMember(team3.getId(), character.getId(), 20, 30, 40, 10)
        );

        playerCharacterStatsDAO.mergeCalculate();

        LadderDistinctCharacter foundCharacter = ladderCharacterDAO.findDistinctCharacters("name").get(0);
        assertEquals(Race.ZERG, foundCharacter.getMembers().getFavoriteRace());
    }

}
