// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MatchIT
{

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProTeamDAO proTeamDAO;

    @Autowired
    private ProTeamMemberDAO proTeamMemberDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private SC2MapDAO mapDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

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
    public void testMatchesChain()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE, BaseLeague.LeagueType.SILVER),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_4V4),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.US),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_2V2),
            TeamType.RANDOM,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.KR),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Division division4v4 = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_4V4, TeamType.ARRANGED, 10)
            .get();
        Division division4v4_2 = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_4V4, TeamType.ARRANGED, 11)
            .get();
        Division division2v2 = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.US, QueueType.LOTV_2V2, TeamType.RANDOM, 0)
            .get();
        Division division1v1 = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.KR, QueueType.LOTV_1V1, TeamType.ARRANGED, 20)
            .get();
        Division division1v1_2 = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_1V1, TeamType.ARRANGED, 10)
            .get();
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        Account acc4 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#4"));
        Account acc5 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#5"));
        Account acc6 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#6"));
        Account acc7 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#7"));
        Account acc8 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#8"));
        Account acc9 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#9"));
        Account acc10 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#10"));
        ProPlayer proPlayer = new ProPlayer(null, 1L, "proNickname", "proName");
        proPlayerDAO.merge(proPlayer);

        //data should be updated
        proTeamDAO.merge(new ProTeam(null, 2L, "proTeamNam e", "proTeamShortName_remove"));
        ProTeam proTeam = proTeamDAO.merge(new ProTeam(null, 1L, "proTeamName", "proTeamShortName"));
        proTeamMemberDAO.merge(new ProTeamMember(proTeam.getId(), proPlayer.getId()));
        proPlayerAccountDAO.link(proPlayer.getId(), "tag#1");
        Clan clan = clanDAO.merge(new Clan(null, "clanTag", Region.EU, "clanName"))[0];
        PlayerCharacter charEu1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter charEu2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter charEu3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));
        PlayerCharacter charEu4 = playerCharacterDAO.merge(new PlayerCharacter(null, acc4.getId(), Region.EU, 4L, 4, "name#4"));
        PlayerCharacter charEu5 = playerCharacterDAO.merge(new PlayerCharacter(null, acc5.getId(), Region.EU, 5L, 5, "name#5"));
        PlayerCharacter charEu6 = playerCharacterDAO.merge(new PlayerCharacter(null, acc6.getId(), Region.EU, 6L, 6, "name#6"));
        PlayerCharacter charEu7 = playerCharacterDAO.merge(new PlayerCharacter(null, acc7.getId(), Region.EU, 7L, 7, "name#7"));
        PlayerCharacter charEu8 = playerCharacterDAO.merge(new PlayerCharacter(null, acc8.getId(), Region.EU, 8L, 8, "name#8"));
        PlayerCharacter charEu9 = playerCharacterDAO.merge(new PlayerCharacter(null, acc9.getId(), Region.EU, 9L, 9, "name#9"));
        PlayerCharacter charEu10 = playerCharacterDAO.merge(new PlayerCharacter(null, acc10.getId(), Region.EU, 10L, 10, "name#10"));
        PlayerCharacter charUs1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.US, 1L, 1, "name#1"));
        PlayerCharacter charUs2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.US, 2L, 2, "name#2"));
        PlayerCharacter charUs3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.US, 3L, 3, "name#3"));
        PlayerCharacter charUs4 = playerCharacterDAO.merge(new PlayerCharacter(null, acc4.getId(), Region.US, 4L, 4, "name#4"));
        PlayerCharacter charKr1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.KR, 1L, 1, "name#1"));
        PlayerCharacter charKr2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.KR, 2L, 2, "name#2"));
        PlayerCharacter charKr3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.KR, 3L, 3, "name#3"));
        PlayerCharacter charKr4 = playerCharacterDAO.merge(new PlayerCharacter(null, acc4.getId(), Region.KR, 4L, 4, "name#4"));
        clanMemberDAO.merge
        (
            new ClanMember(charEu1.getId(), clan.getId()),
            new ClanMember(charEu2.getId(), clan.getId())
        );
        Team team4v4Win = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charEu1.getBattlenetId(), charEu1.getRealm(), charEu1.getName()),
                new BlizzardPlayerCharacter(charEu2.getBattlenetId(), charEu2.getRealm(), charEu2.getName()),
                new BlizzardPlayerCharacter(charEu3.getBattlenetId(), charEu3.getRealm(), charEu3.getName()),
                new BlizzardPlayerCharacter(charEu4.getBattlenetId(), charEu4.getRealm(), charEu4.getName())
            }),
            division4v4.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team4v4Loss = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charEu5.getBattlenetId(), charEu5.getRealm(), charEu5.getName()),
                new BlizzardPlayerCharacter(charEu6.getBattlenetId(), charEu6.getRealm(), charEu6.getName()),
                new BlizzardPlayerCharacter(charEu7.getBattlenetId(), charEu7.getRealm(), charEu7.getName()),
                new BlizzardPlayerCharacter(charEu8.getBattlenetId(), charEu8.getRealm(), charEu8.getName())
            }),
            division4v4_2.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))[0];
        Team team2v2Win1 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charUs1.getBattlenetId(), charUs1.getRealm(), charUs1.getName())
            }),
            division2v2.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team2v2Win2 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charUs2.getBattlenetId(), charUs2.getRealm(), charUs2.getName())
            }),
            division2v2.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team2v2Loss1 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charUs3.getBattlenetId(), charUs3.getRealm(), charUs3.getName())
            }),
            division2v2.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))[0];
        Team team2v2Loss2 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charUs4.getBattlenetId(), charUs4.getRealm(), charUs4.getName())
            }),
            division2v2.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))[0];

        Team team1v1Win = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.KR,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charKr1.getBattlenetId(), charKr1.getRealm(), charKr1.getName())
            }, Race.TERRAN),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team1v1Loss = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.KR,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charKr2.getBattlenetId(), charKr2.getRealm(), charKr2.getName())
            }, Race.PROTOSS),
            division1v1.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))[0];

        Team team1v1Win4 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charEu9.getBattlenetId(), charEu9.getRealm(), charEu9.getName())
            }, Race.TERRAN),
            division1v1_2.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team1v1Loss4 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charEu10.getBattlenetId(), charEu10.getRealm(), charEu10.getName())
            }, Race.PROTOSS),
            division1v1_2.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))[0];

        Team team1v1WinInvalidState = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charKr3.getBattlenetId(), charKr3.getRealm(), charKr3.getName())
            }, Race.TERRAN),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        Team team1v1LossInvalidState = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(charKr4.getBattlenetId(), charKr4.getRealm(), charKr4.getName())
            }, Race.PROTOSS),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))[0];
        teamMemberDAO.merge
        (
            new TeamMember(team4v4Win.getId(), charEu1.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Win.getId(), charEu2.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Win.getId(), charEu3.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Win.getId(), charEu4.getId(), 1, 0, 0, 0),

            new TeamMember(team4v4Loss.getId(), charEu5.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Loss.getId(), charEu6.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Loss.getId(), charEu7.getId(), 1, 0, 0, 0),
            new TeamMember(team4v4Loss.getId(), charEu8.getId(), 1, 0, 0, 0),

            new TeamMember(team2v2Win1.getId(), charUs1.getId(), 1, 0, 0, 0),
            new TeamMember(team2v2Win2.getId(), charUs2.getId(), 1, 0, 0, 0),
            new TeamMember(team2v2Loss1.getId(), charUs3.getId(), 1, 0, 0, 0),
            new TeamMember(team2v2Loss2.getId(), charUs4.getId(), 1, 0, 0, 0),

            new TeamMember(team1v1Win.getId(), charKr1.getId(), 1, 0, 0, 0),
            new TeamMember(team1v1Loss.getId(), charKr2.getId(), 1, 0, 0, 0),

            new TeamMember(team1v1Win4.getId(), charEu9.getId(), 1, 0, 0, 0),
            new TeamMember(team1v1Loss4.getId(), charEu10.getId(), 1, 0, 0, 0),

            new TeamMember(team1v1WinInvalidState.getId(), charKr3.getId(), 1, 0, 0, 0),
            new TeamMember(team1v1LossInvalidState.getId(), charKr4.getId(), 1, 0, 0, 0)
        );
        OffsetDateTime now = OffsetDateTime.now().minusSeconds(1);
        SC2Map map1v1_1 = mapDAO.merge(new SC2Map(null, "map1v1_1"))[0];
        SC2Map map1v1_2 = mapDAO.merge(new SC2Map(null, "map1v1_2"))[0];
        SC2Map map1v1_3 = mapDAO.merge(new SC2Map(null, "map1v1_3"))[0];
        SC2Map map1v1_4 = mapDAO.merge(new SC2Map(null, "map1v1_4"))[0];
        SC2Map map1v1_5 = mapDAO.merge(new SC2Map(null, "map1v1_5"))[0];
        SC2Map map1v1_6 =mapDAO.merge(new SC2Map(null, "map1v1_6"))[0];
        SC2Map map2v2 = mapDAO.merge(new SC2Map(null, "map2v2"))[0];
        SC2Map map4v4 = mapDAO.merge(new SC2Map(null, "map4v4"))[0];
        Match[] matches1 = matchDAO.merge
        (
            new Match(null, now, BaseMatch.MatchType._4V4, map4v4.getId(), Region.EU), //insert
            new Match(null, now, BaseMatch.MatchType._4V4, map4v4.getId(), Region.EU), //identical, insert without errors
            new Match(null, now, BaseMatch.MatchType._2V2, map2v2.getId(), Region.US) //insert
        );
        Arrays.sort(matches1, Match.NATURAL_ID_COMPARATOR);
        assertEquals(3, matches1.length);
        assertEquals(matches1[1].getId(), matches1[2].getId()); //clone object is updated, ASC order
        Match match4v4 = new Match(null, now, BaseMatch.MatchType._4V4, map4v4.getId(), Region.EU);
        Match match2v2 = new Match(null, now, BaseMatch.MatchType._2V2, map2v2.getId(), Region.US);
        Match match1v1_1 = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.KR);
        Match match1v1_2 = new Match(null, now.plusSeconds(1), BaseMatch.MatchType._1V1, map1v1_2.getId(), Region.KR);
        Match match1v1_3 = new Match(null, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 2 + 1),
            BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.KR);
        Match match1v1_4 = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        Match match1v1InvalidState = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_3.getId(), Region.KR);
        Match match1v1InvalidCount =  new Match(null, now, BaseMatch.MatchType._1V1, map1v1_4.getId(), Region.KR);
        Match match1v1InvalidDecision = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_5.getId(), Region.KR);
        Match match1v1UnidentifiedMembers = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_6.getId(), Region.KR);
        matchDAO.merge
        (
            match4v4, //update
            match2v2, //update
            match1v1_1, //insert...
            match1v1_2,
            match1v1_3,
            match1v1_4,
            match1v1InvalidState,
            match1v1InvalidCount,
            match1v1InvalidDecision,
            match1v1UnidentifiedMembers
        );
        matchParticipantDAO.merge
        (
            //skip duplicate, order by decision ASC
            new MatchParticipant(match4v4.getId(), charEu1.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match4v4.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match4v4.getId(), charEu2.getId(), BaseMatch.Decision.LOSS)
        );
        matchParticipantDAO.merge
        (
            new MatchParticipant(match4v4.getId(), charEu1.getId(), BaseMatch.Decision.WIN), //nothing
            new MatchParticipant(match4v4.getId(), charEu2.getId(), BaseMatch.Decision.WIN), //update, was loss
            new MatchParticipant(match4v4.getId(), charEu3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match4v4.getId(), charEu4.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match4v4.getId(), charEu5.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match4v4.getId(), charEu6.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match4v4.getId(), charEu7.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match4v4.getId(), charEu8.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match2v2.getId(), charUs1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match2v2.getId(), charUs2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match2v2.getId(), charUs3.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match2v2.getId(), charUs4.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_1.getId(), charKr1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_1.getId(), charKr2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_2.getId(), charKr1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_2.getId(), charKr2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_3.getId(), charKr1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_3.getId(), charKr2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_4.getId(), charEu9.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_4.getId(), charEu10.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1InvalidState.getId(), charKr3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1InvalidState.getId(), charKr4.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1InvalidCount.getId(), charKr1.getId(), BaseMatch.Decision.WIN),

            new MatchParticipant(match1v1InvalidDecision.getId(), charKr1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1InvalidDecision.getId(), charKr2.getId(), BaseMatch.Decision.WIN),

            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr2.getId(), BaseMatch.Decision.LOSS)
        );
        TeamState state4v4Win = TeamState.of(team4v4Win, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES));
        TeamState state4v4Loss = TeamState.of(team4v4Loss, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES));
        TeamState state4v4Loss2 = TeamState.of(team4v4Loss, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES - 1));
        TeamState state2v2Win1 = TeamState.of(team2v2Win1, now);
        TeamState state2v2Win2 = TeamState.of(team2v2Win2, now);
        TeamState state2v2Loss1 = TeamState.of(team2v2Loss1, now);
        TeamState state2v2Loss2 = TeamState.of(team2v2Loss2, now);
        TeamState state1v1Win0 = TeamState.of(team1v1Win, now.minusSeconds(1));
        //should be picked as the closest state because it's after the match
        TeamState state1v1Win1 = TeamState.of(team1v1Win, now.plusSeconds(2));
        TeamState state1v1Loss1 = TeamState.of(team1v1Loss, now.plusSeconds(1));
        TeamState state1v1Win2 = TeamState.of(team1v1Win, now.plusSeconds(30));
        TeamState state1v1Win3 = TeamState.of(team1v1Win, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 2 + 1));
        TeamState state1v1Loss3 = TeamState.of(team1v1Loss, now.plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 2 + 1));
        TeamState state1v1Win4 = TeamState.of(team1v1Win4, now);
        TeamState state1v1Loss4 = TeamState.of(team1v1Loss4, now);
        teamStateDAO.saveState(
            state4v4Win, state4v4Loss, state4v4Loss2,
            state2v2Win1, state2v2Win2, state2v2Loss1, state2v2Loss2,
            state1v1Win0, state1v1Win1, state1v1Loss1, state1v1Win2, state1v1Win3, state1v1Loss3, state1v1Win4, state1v1Loss4,
            TeamState.of(team1v1WinInvalidState, now.minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES + 1)),
            TeamState.of(team1v1LossInvalidState, now.minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES + 1))
        );

        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, now);
        //merging eralier will prevent all participants from identifying. Merging here to simulate partly
        //unidentified team
        matchParticipantDAO.merge(
            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr4.getId(), BaseMatch.Decision.LOSS)
        );

        List<LadderMatch> matches4v4 = ladderMatchDAO.findMatchesByCharacterId(charEu1.getId(), OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1).getResult();
        assertEquals(1, matches4v4.size());
        assertEquals(match4v4, matches4v4.get(0).getMatch());
        assertEquals(8, matches4v4.get(0).getParticipants().size());
        List<LadderMatchParticipant> winners1 = matches4v4.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers1 = matches4v4.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners1, 4, 4, team4v4Win, Set.of(state4v4Win), BaseLeague.LeagueType.BRONZE,
            Set.of(charEu1, charEu2, charEu3, charEu4), match4v4);
        verifyMatch(losers1, 4, 4, team4v4Loss, Set.of(state4v4Loss, state4v4Loss2), BaseLeague.LeagueType.SILVER,
            Set.of(charEu5, charEu6, charEu7, charEu8), match4v4);
        assertIterableEquals(matches4v4.stream().map(LadderMatch::getMatch).collect(Collectors.toList()),
            ladderMatchDAO.findMatchesByCharacterId(charEu1.getId(), OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1, BaseMatch.MatchType._4V4).getResult()
                .stream().map(LadderMatch::getMatch).collect(Collectors.toList()));
        assertTrue(ladderMatchDAO.findMatchesByCharacterId(charEu1.getId(), OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1, BaseMatch.MatchType._1V1)
            .getResult().isEmpty());

        List<LadderMatch> matches2v2 = ladderMatchDAO.findMatchesByCharacterId(charUs3.getId(), OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1).getResult();
        assertEquals(1, matches2v2.size());
        assertEquals(match2v2, matches2v2.get(0).getMatch());
        assertEquals(4, matches2v2.get(0).getParticipants().size());
        List<LadderMatchParticipant> winners2 = matches2v2.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers2 = matches2v2.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners2.stream().filter(p-> p.getParticipant().getPlayerCharacterId().equals(charUs1.getId())).collect(Collectors.toList()),
            1, 1, team2v2Win1, Set.of(state2v2Win1), BaseLeague.LeagueType.BRONZE,
            Set.of(charUs1), match2v2);
        verifyMatch(winners2.stream().filter(p-> p.getParticipant().getPlayerCharacterId().equals(charUs2.getId())).collect(Collectors.toList()),
            1, 1, team2v2Win2, Set.of(state2v2Win2), BaseLeague.LeagueType.BRONZE,
            Set.of(charUs2), match2v2);
        verifyMatch(losers2.stream().filter(p-> p.getParticipant().getPlayerCharacterId().equals(charUs3.getId())).collect(Collectors.toList()),
            1, 1, team2v2Loss1, Set.of(state2v2Loss1), BaseLeague.LeagueType.BRONZE,
            Set.of(charUs3), match2v2);
        verifyMatch(losers2.stream().filter(p-> p.getParticipant().getPlayerCharacterId().equals(charUs4.getId())).collect(Collectors.toList()),
            1, 1, team2v2Loss2, Set.of(state2v2Loss2), BaseLeague.LeagueType.BRONZE,
            Set.of(charUs4), match2v2);

        List<LadderMatch> matches1v1 = ladderMatchDAO.findMatchesByCharacterId(charKr1.getId(),
            OffsetDateTime.now().plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 2 + 1),
            BaseMatch.MatchType._1V1, 0, 0, 1).getResult();
        assertEquals(6, matches1v1.size());
        assertEquals(match1v1_3, matches1v1.get(0).getMatch());
        assertEquals(2, matches1v1.get(0).getParticipants().size());
        assertEquals(match1v1_2, matches1v1.get(1).getMatch());
        assertEquals(2, matches1v1.get(1).getParticipants().size());
        assertEquals(match1v1_1, matches1v1.get(5).getMatch());
        assertEquals(2, matches1v1.get(5).getParticipants().size());
        assertEquals(match1v1InvalidCount, matches1v1.get(4).getMatch());
        assertEquals(1, matches1v1.get(4).getParticipants().size());
        assertEquals(match1v1InvalidDecision, matches1v1.get(3).getMatch());
        assertEquals(2, matches1v1.get(3).getParticipants().size());
        assertEquals(match1v1UnidentifiedMembers, matches1v1.get(2).getMatch());
        assertEquals(4, matches1v1.get(2).getParticipants().size());

        List<LadderMatchParticipant> winners3_3 = matches1v1.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers3_3 = matches1v1.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners3_3, 1, 1, team1v1Win, Set.of(state1v1Win3), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr1), match1v1_3);
        verifyMatch(losers3_3, 1, 1, team1v1Loss, Set.of(state1v1Loss3), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr2), match1v1_3);

        List<LadderMatchParticipant> winners3_1 = matches1v1.get(1).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers3_1 = matches1v1.get(1).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners3_1, 1, 1, team1v1Win, Set.of(state1v1Win1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr1), match1v1_2);
        verifyMatch(losers3_1, 1, 1, team1v1Loss, Set.of(state1v1Loss1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr2), match1v1_2);

        List<LadderMatchParticipant> winners3_2 = matches1v1.get(5).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers3_2 = matches1v1.get(5).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners3_2, 1, 1, team1v1Win, Set.of(state1v1Win1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr1), match1v1_1);
        verifyMatch(losers3_2, 1, 1, team1v1Loss, Set.of(state1v1Loss1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr2), match1v1_1);

        List<LadderMatchParticipant> winnersInvalidCount = matches1v1.get(4).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losersInvalidCount = matches1v1.get(4).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winnersInvalidCount, 1, 1, team1v1Win, Set.of(state1v1Win1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr1), match1v1InvalidCount);
        assertTrue(losersInvalidCount.isEmpty());

        List<LadderMatchParticipant> winnersInvalidDecision = matches1v1.get(3).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losersInvalidDecision = matches1v1.get(3).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winnersInvalidDecision.subList(1, 2), 1, 1, team1v1Win, Set.of(state1v1Win1),
            BaseLeague.LeagueType.BRONZE, Set.of(charKr1), match1v1InvalidDecision);
        verifyMatch(winnersInvalidDecision.subList(0, 1), 1, 1, team1v1Loss, Set.of(state1v1Loss1),
            BaseLeague.LeagueType.BRONZE, Set.of(charKr2), match1v1InvalidDecision);
        assertTrue(losersInvalidDecision.isEmpty());

        List<LadderMatchParticipant> winnersUnidentified1 = matches1v1.get(2).getParticipants().stream()
            .filter(p->p.getTeam() != null && p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losersUnidentified1 = matches1v1.get(2).getParticipants().stream()
            .filter(p->p.getTeam() != null && p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        List<LadderMatchParticipant> winnersUnidentified2 = matches1v1.get(2).getParticipants().stream()
            .filter(p->p.getTeam() == null && p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losersUnidentified2 = matches1v1.get(2).getParticipants().stream()
            .filter(p->p.getTeam() == null && p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winnersUnidentified1, 1, 1, team1v1Win, Set.of(state1v1Win1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr1), match1v1UnidentifiedMembers);
        verifyMatch(losersUnidentified1, 1, 1, team1v1Loss, Set.of(state1v1Loss1), BaseLeague.LeagueType.BRONZE,
            Set.of(charKr2), match1v1UnidentifiedMembers);

        assertEquals(1, winnersUnidentified2.size());
        assertEquals(match1v1UnidentifiedMembers.getId(), winnersUnidentified2.get(0).getParticipant().getMatchId());
        assertNull(winnersUnidentified2.get(0).getTeam());
        assertEquals(charKr3.getId(), winnersUnidentified2.get(0).getParticipant().getPlayerCharacterId());

        assertEquals(1, losersUnidentified2.size());
        assertEquals(match1v1UnidentifiedMembers.getId(), losersUnidentified2.get(0).getParticipant().getMatchId());
        assertNull(losersUnidentified2.get(0).getTeam());
        assertEquals(charKr4.getId(), losersUnidentified2.get(0).getParticipant().getPlayerCharacterId());


        List<LadderMatch> matches1v1_2 = ladderMatchDAO.findMatchesByCharacterId(charEu9.getId(), OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1).getResult();
        assertEquals(1, matches1v1_2.size());
        assertEquals(match1v1_4, matches1v1_2.get(0).getMatch());
        assertEquals(2, matches1v1_2.get(0).getParticipants().size());

        List<LadderMatchParticipant> winners4_1 = matches1v1_2.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.WIN).collect(Collectors.toList());
        List<LadderMatchParticipant> losers4_1 = matches1v1_2.get(0).getParticipants().stream()
            .filter(p->p.getParticipant().getDecision() == BaseMatch.Decision.LOSS).collect(Collectors.toList());
        verifyMatch(winners4_1, 1, 1, team1v1Win4, Set.of(state1v1Win4), BaseLeague.LeagueType.BRONZE,
            Set.of(charEu9), match1v1_4);
        verifyMatch(losers4_1, 1, 1, team1v1Loss4, Set.of(state1v1Loss4), BaseLeague.LeagueType.BRONZE,
            Set.of(charEu10), match1v1_4);
    }

    private void verifyMatch
    (
        List<LadderMatchParticipant> participants,
        int participantCount,
        int teamMemberCount,
        Team team,
        Set<TeamState> states,
        BaseLeague.LeagueType stateLeague,
        Set<PlayerCharacter> characters,
        Match match
    )
    {
        assertEquals(participantCount, participants.size());
        participants.stream().map(LadderMatchParticipant::getTeam).forEach(t->assertEquals(team, t));
        participants.stream().map(p->p.getTeam().getMembers()).forEach(m->assertEquals(teamMemberCount, m.size()));
        //contains the state
        participants.stream().map(LadderMatchParticipant::getTeamState).forEach(s->{
            assertTrue(states.stream().anyMatch(ss->
                s.getTeamState().equals(ss)
                && s.getTeamState().getRating().equals(ss.getRating())
                && s.getLeague().getType().equals(stateLeague)));
        });
        //every character has its match participant
        for(PlayerCharacter character : characters)
            assertTrue(participants.stream()
                .map(LadderMatchParticipant::getParticipant)
                .anyMatch(p->p.getPlayerCharacterId().equals(character.getId())));
        //every character has its team member in every team
        for(PlayerCharacter character : characters)
            participants.stream()
                .map(LadderMatchParticipant::getTeam)
                .forEach(t->assertTrue(t.getMembers().stream().anyMatch(p->p.getCharacter().getId().equals(character.getId()))));

        //acc1 is a pro player
        participants.stream()
            .flatMap(p->p.getTeam().getMembers().stream())
            .filter(m->m.getAccount().getBattleTag().equalsIgnoreCase("tag#1"))
            .forEach(m->{
                assertEquals("proNickname", m.getProNickname());
                assertEquals("proTeamShortName", m.getProTeam());
            });
        //char1 and char2 have a clan
        participants.stream()
            .flatMap(p->p.getTeam().getMembers().stream())
            .filter(m->
                m.getCharacter().getRegion() == Region.EU
                && (m.getCharacter().getName().equalsIgnoreCase("name#1") || m.getCharacter().getName().equalsIgnoreCase("name#2"))
            )
            .forEach(m->{
                assertNotNull(m.getClan());
                assertEquals("clanTag", m.getClan().getTag());
                assertEquals(Region.EU, m.getClan().getRegion());
                assertEquals("clanName", m.getClan().getName());
            });

        participants.stream().map(LadderMatchParticipant::getParticipant).forEach(p->assertEquals(match.getId(), p.getMatchId()));
    }

    @Test
    public void testDuration()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));

        PlayerCharacter charEu1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter charEu2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter charEu3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));

        SC2Map map1v1_1 = mapDAO.merge(new SC2Map(null, "map1v1_1"))[0];

        OffsetDateTime now = OffsetDateTime.now();
        Match match1v1_1 = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        Match match1v1_2 = new Match(null, match1v1_1.getDate().minusSeconds(300), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        Match match1v1_3 = new Match(null, match1v1_2.getDate().minusSeconds(400), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        //null duration, previous match is too old
        Match match1v1_4 = new Match(null, match1v1_3.getDate().minusSeconds(MatchDAO.DURATION_OFFSET + 1), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        //null duration, there is no previous match
        Match match1v1_5 = new Match(null, match1v1_4.getDate().minusSeconds(MatchDAO.DURATION_MAX + 1), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);

        matchDAO.merge
        (
            match1v1_1,
            match1v1_2,
            match1v1_3,
            match1v1_4,
            match1v1_5
        );

        matchParticipantDAO.merge
        (
            new MatchParticipant(match1v1_1.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_1.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_2.getId(), charEu3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_2.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_3.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_4.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_4.getId(), charEu1.getId(), BaseMatch.Decision.WIN),

            new MatchParticipant(match1v1_5.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_5.getId(), charEu2.getId(), BaseMatch.Decision.LOSS)
        );

        assertEquals(3, matchDAO.updateDuration(now.minusDays(1)));

        List<LadderMatch> matches = ladderMatchDAO
            .findMatchesByCharacterId(charEu1.getId(), now.plusSeconds(1), BaseMatch.MatchType._1V1, 0, 0, 1)
            .getResult();

        assertEquals(300 - MatchDAO.DURATION_OFFSET, matches.get(0).getMatch().getDuration());
        assertEquals(1, matches.get(1).getMatch().getDuration());
        assertNull(matches.get(2).getMatch().getDuration());
        assertNull(matches.get(3).getMatch().getDuration());
    }

    @Test
    public void testRatingChange()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_4V4),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            2
        );

        OffsetDateTime now = OffsetDateTime.now();
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._4V4,
            1L, 2L,
            new long[]{1, 2, 3, 4}, new long[]{5, 6, 7, 8},
            now, Region.EU, 10, 1,
            10,
            120, 80, //5
            115, 85, //null, identical values
            115, 85, //5
            110, 90, //10
            100, 100, //10
            90, 110, //null, rating change does not match the decision
            100, 100, //null, prev match is invalid
            90, 110, //null, unidentified match
            85, 115, //5
            80, 120, //null, excluded by "from" timestamp
            75, 125 //null, out of scope values
        );

        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, OffsetDateTime.now().minusYears(1));
        //unidentify match
        assertEquals(8, template.update("UPDATE match_participant SET team_id = NULL WHERE match_id = 8"));

        //4mmr changes * 8 participants(4v4)
        assertEquals(40, matchParticipantDAO.calculateRatingDifference(now.minusSeconds(8)));

        List<LadderMatch> matches = ladderMatchDAO
            .findMatchesByCharacterId(1L, now.plusSeconds(1), BaseMatch.MatchType._1V1, 0, 0, 1).getResult();
        assertEquals(10, matches.size());
        verifyRatingChange(matches.get(0), 5);
        verifyRatingChange(matches.get(1), null);
        verifyRatingChange(matches.get(2), 5);
        verifyRatingChange(matches.get(3), 10);
        verifyRatingChange(matches.get(4), 10);
        verifyRatingChange(matches.get(5), null);
        verifyRatingChange(matches.get(6), null);
        verifyRatingChange(matches.get(7), null);
        verifyRatingChange(matches.get(8), 5);
        verifyRatingChange(matches.get(9), null);
    }

    public void verifyRatingChange(LadderMatch match, Integer ratingDifference)
    {
        if(ratingDifference == null)
        {
            assertTrue(match.getParticipants().stream()
                .allMatch(p->p.getParticipant().getRatingChange() == null));
        } else
        {
            assertTrue(match.getParticipants().stream()
                .allMatch(p->p.getParticipant().getRatingChange() ==
                    (p.getParticipant().getDecision() == BaseMatch.Decision.WIN ? ratingDifference : -ratingDifference)));
        }
    }

}
