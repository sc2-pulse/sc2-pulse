// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
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
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
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
    private LadderUpdateDAO ladderUpdateDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    /*TODO
        Split this abomination into proper tests
     */
    @Test
    public void testMatchesChain()
    throws Exception
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
        proTeamMemberDAO.merge(Set.of(new ProTeamMember(proTeam.getId(), proPlayer.getId())));
        proPlayerAccountDAO.link(proPlayer.getId(), "tag#1");
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clanTag", Region.EU, "clanName")))
            .iterator().next();
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
        String allCharIds = Stream.of
        (
            charEu1, charEu2, charEu3, charEu4, charEu5, charEu6, charEu7, charEu8, charEu9,
            charEu10, charUs1, charUs2, charUs3, charUs4, charKr1, charKr2, charKr3, charKr4
        )
            .map(PlayerCharacter::getId)
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        clanMemberDAO.merge(Set.of(
            new ClanMember(charEu1.getId(), clan.getId()),
            new ClanMember(charEu2.getId(), clan.getId())
        ));
        Team team4v4Win = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charEu1.getRealm(), charEu1.getBattlenetId()),
                new TeamLegacyIdEntry(charEu2.getRealm(), charEu2.getBattlenetId()),
                new TeamLegacyIdEntry(charEu3.getRealm(), charEu3.getBattlenetId()),
                new TeamLegacyIdEntry(charEu4.getRealm(), charEu4.getBattlenetId())
            )),
            division4v4.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team4v4Loss = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charEu5.getRealm(), charEu5.getBattlenetId()),
                new TeamLegacyIdEntry(charEu6.getRealm(), charEu6.getBattlenetId()),
                new TeamLegacyIdEntry(charEu7.getRealm(), charEu7.getBattlenetId()),
                new TeamLegacyIdEntry(charEu8.getRealm(), charEu8.getBattlenetId())
            )),
            division4v4_2.getId(), 2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team2v2Win1 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charUs1.getRealm(), charUs1.getBattlenetId())
            )),
            division2v2.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team2v2Win2 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charUs2.getRealm(), charUs2.getBattlenetId())
            )),
            division2v2.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team2v2Loss1 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charUs3.getRealm(), charUs3.getBattlenetId())
            )),
            division2v2.getId(), 2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team2v2Loss2 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.RANDOM),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charUs4.getRealm(), charUs4.getBattlenetId())
            )),
            division2v2.getId(), 2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();

        Team team1v1Win = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.KR,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charKr1.getRealm(), charKr1.getBattlenetId(), Race.TERRAN)
            )),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team1v1Loss = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.KR,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charKr2.getRealm(), charKr2.getBattlenetId(), Race.PROTOSS)
            )),
            division1v1.getId(), 2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();

        Team team1v1Win4 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charEu9.getRealm(), charEu9.getBattlenetId(), Race.TERRAN)
            )),
            division1v1_2.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team1v1Loss4 = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charEu10.getRealm(), charEu10.getBattlenetId(), Race.PROTOSS)
            )),
            division1v1_2.getId(), 2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();

        Team team1v1WinInvalidState = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charKr3.getRealm(), charKr3.getBattlenetId(), Race.TERRAN)
            )),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        Team team1v1LossInvalidState = teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.standard(Set.of(
                new TeamLegacyIdEntry(charKr4.getRealm(), charKr4.getBattlenetId(), Race.PROTOSS)
            )),
            division1v1.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
        teamMemberDAO.merge(Set.of(
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
        ));
        OffsetDateTime now = SC2Pulse.offsetDateTime().minusSeconds(1);
        SC2Map map1v1_1 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_1"))).iterator().next();
        SC2Map map1v1_2 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_2"))).iterator().next();
        SC2Map map1v1_3 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_3"))).iterator().next();
        SC2Map map1v1_4 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_4"))).iterator().next();
        SC2Map map1v1_5 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_5"))).iterator().next();
        SC2Map map1v1_6 =mapDAO.merge(Set.of(new SC2Map(null, "map1v1_6"))).iterator().next();
        SC2Map map2v2 = mapDAO.merge(Set.of(new SC2Map(null, "map2v2"))).iterator().next();
        SC2Map map4v4 = mapDAO.merge(Set.of(new SC2Map(null, "map4v4"))).iterator().next();
        Match[] matches1 = matchDAO.merge(new LinkedHashSet<>(List.of(
            new Match(null, now, BaseMatch.MatchType._4V4, map4v4.getId(), Region.EU), //insert
            new Match(null, now, BaseMatch.MatchType._4V4, map4v4.getId(), Region.EU), //identical, insert without errors
            new Match(null, now, BaseMatch.MatchType._2V2, map2v2.getId(), Region.US) //insert
        )))
            .toArray(Match[]::new);
        Arrays.sort(matches1, Match.NATURAL_ID_COMPARATOR);
        assertEquals(2, matches1.length);
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
        matchDAO.merge(Set.of(
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
        ));
        matchParticipantDAO.merge(Set.of(
            new MatchParticipant(match4v4.getId(), charEu1.getId(), BaseMatch.Decision.LOSS),
            new MatchParticipant(match4v4.getId(), charEu2.getId(), BaseMatch.Decision.LOSS)
        ));
        matchParticipantDAO.merge(Set.of(
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
        ));
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
        teamStateDAO.saveState(Set.of(
            state4v4Win, state4v4Loss, state4v4Loss2,
            state2v2Win1, state2v2Win2, state2v2Loss1, state2v2Loss2,
            state1v1Win0, state1v1Win1, state1v1Loss1, state1v1Win2, state1v1Win3, state1v1Loss3, state1v1Win4, state1v1Loss4,
            TeamState.of(team1v1WinInvalidState, now.minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES + 1)),
            TeamState.of(team1v1LossInvalidState, now.minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES + 1))
        ));

        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, now);
        //merging eralier will prevent all participants from identifying. Merging here to simulate partly
        //unidentified team
        matchParticipantDAO.merge(Set.of(
            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1UnidentifiedMembers.getId(), charKr4.getId(), BaseMatch.Decision.LOSS)
        ));

        List<LadderMatch> matches4v4 = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=" + allCharIds
                + "&type=_4V4"
        );
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
        assertIterableEquals
        (
            matches4v4.stream().map(LadderMatch::getMatch).collect(Collectors.toList()),
            WebServiceTestUtil.getObject
            (
                mvc, objectMapper, new TypeReference<List<LadderMatch>>(){},
                "/api/group/match"
                    + "?characterId=" + allCharIds
                    + "&type=_4V4"
            ).stream().map(LadderMatch::getMatch).collect(Collectors.toList())
        );
        mvc.perform
        (
            get("/api/group/match")
                .queryParam("characterId", String.valueOf(charEu1.getId()))
                .queryParam("type", BaseMatch.MatchType._1V1.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound());

        List<LadderMatch> matches2v2 = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=" + allCharIds
                + "&type=_2V2"
        );
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

        List<LadderMatch> matches1v1 = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=" + charKr1.getId()
                + "&dateCursor=" + SC2Pulse.offsetDateTime()
                    .plusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 2 + 1)
        );
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


        List<LadderMatch> matches1v1_2 = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=" + charEu9.getId()
        );
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
                Assertions.assertThat(m.getClan()).usingRecursiveComparison().isEqualTo(new Clan(
                   1, "clanTag", Region.EU, "clanName"
                ));
            });

        participants.stream().map(LadderMatchParticipant::getParticipant).forEach(p->assertEquals(match.getId(), p.getMatchId()));
    }

    @Test
    public void testDuration()
    throws Exception
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));

        PlayerCharacter charEu1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter charEu2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter charEu3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));

        SC2Map map1v1_1 = mapDAO.merge(Set.of(new SC2Map(null, "map1v1_1"))).iterator().next();

        OffsetDateTime now = SC2Pulse.offsetDateTime();
        Match match1v1_1 = new Match(null, now, BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        Match match1v1_2 = new Match(null, match1v1_1.getDate().minusSeconds(300), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        Match match1v1_3 = new Match(null, match1v1_2.getDate().minusSeconds(400), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        //null duration, previous match is too old
        Match match1v1_4 = new Match(null, match1v1_3.getDate().minusSeconds(MatchDAO.DURATION_OFFSET + 1), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);
        //null duration, there is no previous match
        Match match1v1_5 = new Match(null, match1v1_4.getDate().minusSeconds(MatchDAO.DURATION_MAX + 1), BaseMatch.MatchType._1V1, map1v1_1.getId(), Region.EU);

        matchDAO.merge(Set.of(
            match1v1_1,
            match1v1_2,
            match1v1_3,
            match1v1_4,
            match1v1_5
        ));

        matchParticipantDAO.merge(Set.of(
            new MatchParticipant(match1v1_1.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_1.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_2.getId(), charEu3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_2.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_3.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_4.getId(), charEu2.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(match1v1_4.getId(), charEu1.getId(), BaseMatch.Decision.WIN),

            new MatchParticipant(match1v1_5.getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(match1v1_5.getId(), charEu2.getId(), BaseMatch.Decision.LOSS)
        ));

        assertEquals(3, matchDAO.updateDuration(now.minusDays(1)));

        List<LadderMatch> matches = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=" + charEu1.getId()
                + "&dateCursor=" + now.plusSeconds(1)
        );

        assertEquals(300 - MatchDAO.DURATION_OFFSET, matches.get(0).getMatch().getDuration());
        assertEquals(1, matches.get(1).getMatch().getDuration());
        assertNull(matches.get(2).getMatch().getDuration());
        assertNull(matches.get(3).getMatch().getDuration());
    }

    @Test
    public void testRatingChange()
    throws Exception
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

        OffsetDateTime now = SC2Pulse.offsetDateTime();
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

        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, SC2Pulse.offsetDateTime().minusYears(1));
        //unidentify match
        assertEquals(8, template.update("UPDATE match_participant SET team_id = NULL WHERE match_id = 8"));

        //4mmr changes * 8 participants(4v4)
        assertEquals(40, matchParticipantDAO.calculateRatingDifference(now.minusSeconds(8)));

        List<LadderMatch> matches = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, new TypeReference<>(){},
            "/api/group/match"
                + "?characterId=1"
                + "&dateCursor=" + now.plusSeconds(1)
        );
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

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void whenLadderUpdateIsPresent_thenUseItsDurationAsMaxIdentificationOffset(boolean inRange)
    {
        OffsetDateTime odt = SC2Pulse.offsetDateTime();
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE, BaseLeague.LeagueType.SILVER),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        template.update("DELETE FROM team_state");
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._1V1,
            1L, 2L,
            new long[]{1L}, new long[]{2L},
            odt,
            Region.EU,
            1, 1, 1, 1, 1
        );
        ladderUpdateDAO.create(Set.of(
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                odt.plusMinutes(1),
                Duration.ofMinutes(1)
            ),
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                odt.plusMinutes(2),
                Duration.ofMinutes(2)
            ),
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                odt.plusMinutes(3),
                Duration.ofMinutes(1)
            ),
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.SILVER,
                odt.plusMinutes(1),
                Duration.ofSeconds(59)
            )
        ));
        OffsetDateTime maxOdt = odt.plusMinutes(4);
        OffsetDateTime targetOdt = inRange ? maxOdt.minusSeconds(1) : maxOdt.plusSeconds(1);
        template.update("UPDATE team_state SET timestamp = ?", targetOdt);
        //move team2 to silver
        template.update("UPDATE team_state SET division_id = 2 WHERE team_id = 2");
        assertEquals
        (
            inRange ? 1 : 0,
            matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, odt)
        );
        LadderMatch match = ladderMatchDAO.findMatchesByCharacterIds
        (
            Set.of(1L),
            odt.plusSeconds(1),
            BaseMatch.MatchType._1V1, 1, Region.EU,
            0, 1, 10
        ).getResult().get(0);
        match.getParticipants().forEach(p->assertEquals(
            p.getParticipant().getPlayerCharacterId() == 1L && inRange ? targetOdt : null,
            p.getParticipant().getTeamStateDateTime()
        ));
    }

}
