// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.VersusSummary;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.Versus;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class VersusIT
{

    private static MockMvc mvc;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private SC2MapDAO mapDAO;

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    private static Clan clan1, clan2;
    private static Match[] matches;
    private static Team team1, team2, team3, team4;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired DivisionDAO divisionDAO,
        @Autowired ClanDAO clanDAO,
        @Autowired ClanMemberDAO clanMemberDAO,
        @Autowired AccountDAO accountDAO,
        @Autowired PlayerCharacterDAO playerCharacterDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamMemberDAO teamMemberDAO,
        @Autowired MatchDAO matchDAO,
        @Autowired MatchParticipantDAO matchParticipantDAO,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired SC2MapDAO mapDAO,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();

        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );

        Division division1v1= divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_1V1, TeamType.ARRANGED, 10)
            .orElseThrow();

        clan1 = clanDAO.merge(Set.of(new Clan(null, "clanTag1", Region.EU, "clanName1")))
            .iterator().next();
        clan2 = clanDAO.merge(Set.of(new Clan(null, "clanTag2", Region.EU, "clanName2")))
            .iterator() .next();

        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        Account acc4 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#4"));

        PlayerCharacter charEu1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter charEu2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter charEu3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));
        PlayerCharacter charEu4 = playerCharacterDAO.merge(new PlayerCharacter(null, acc4.getId(), Region.EU, 4L, 4, "name#4"));

        clanMemberDAO.merge(Set.of(
            new ClanMember(charEu1.getId(), clan1.getId()),
            new ClanMember(charEu2.getId(), clan1.getId()),
            new ClanMember(charEu3.getId(), clan2.getId())
        ));

        team1 = createTeam(teamDAO, charEu1, division1v1);
        team2 = createTeam(teamDAO, charEu2, division1v1);
        team3 = createTeam(teamDAO, charEu3, division1v1);
        team4 = createTeam(teamDAO, charEu4, division1v1);

        teamMemberDAO.merge(Set.of(
            new TeamMember(team1.getId(), charEu1.getId(), 1, 1, 1, 1),
            new TeamMember(team2.getId(), charEu2.getId(), 1, 1, 1, 1),
            new TeamMember(team3.getId(), charEu3.getId(), 1, 1, 1, 1),
            new TeamMember(team4.getId(), charEu4.getId(), 1, 1, 1, 1)
        ));

        OffsetDateTime now = SC2Pulse.offsetDateTime();
        teamStateDAO.saveState(Set.of(
            new TeamState(team1.getId(), now, division1v1.getId(), 1, 1),
            new TeamState(team2.getId(), now, division1v1.getId(), 1, 1),
            new TeamState(team3.getId(), now, division1v1.getId(), 1, 1),
            new TeamState(team4.getId(), now, division1v1.getId(), 1, 1)
        ));

        SC2Map map = mapDAO.merge(Set.of(new SC2Map(null, "map1"))).iterator().next();

        matches = matchDAO.merge(new LinkedHashSet<>(List.of(
            new Match(null, now.minusSeconds(1), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(2), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(3), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(4), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(5), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(6), BaseMatch.MatchType._2V2, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(7), BaseMatch.MatchType._1V1, map.getId(), Region.EU),
            new Match(null, now.minusSeconds(8), BaseMatch.MatchType.COOP, map.getId(), Region.EU) //invalid match type
        )))
            .toArray(Match[]::new);

        matchParticipantDAO.merge(Set.of(
            new MatchParticipant(matches[0].getId(), charEu2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[0].getId(), charEu3.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(matches[1].getId(), charEu2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[1].getId(), charEu3.getId(), BaseMatch.Decision.LOSS),

            //invalid decisions
            new MatchParticipant(matches[2].getId(), charEu2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[2].getId(), charEu3.getId(), BaseMatch.Decision.WIN),

            //invalid decisions
            new MatchParticipant(matches[6].getId(), charEu2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[6].getId(), charEu3.getId(), BaseMatch.Decision.OBSERVER),

            new MatchParticipant(matches[7].getId(), charEu2.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[7].getId(), charEu3.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(matches[3].getId(), charEu3.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[3].getId(), charEu4.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(matches[4].getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[4].getId(), charEu4.getId(), BaseMatch.Decision.LOSS),

            new MatchParticipant(matches[5].getId(), charEu1.getId(), BaseMatch.Decision.WIN),
            new MatchParticipant(matches[5].getId(), charEu4.getId(), BaseMatch.Decision.LOSS)
        ));

        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, OffsetDateTime.MIN);
    }

    @AfterAll
    public static void afterAll(@Autowired BlizzardSC2API api, @Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    private static Team createTeam(TeamDAO teamDAO, PlayerCharacter character, Division division)
    {
        return teamDAO.merge(Set.of(Team.joined(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST,
            teamDAO.legacyIdOf(Set.of(
                new TeamLegacyIdEntry(character.getRealm(), character.getBattlenetId(), Race.TERRAN)
            )),
            division.getId(), 1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        ))).iterator().next();
    }

    private <T> T getVersus
    (
        Integer[] clans1,
        Set<TeamLegacyUid> teams1,
        Integer[] clans2,
        Set<TeamLegacyUid> teams2,
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        Region regionAnchor,
        int page,
        int pageDiff,
        BaseMatch.MatchType type,
        String resourceType,
        TypeReference<T> typeReference
    )
    throws Exception
    {
        StringBuilder sb = new StringBuilder();
        for(int clan1 : clans1) sb.append("&clan1=").append(clan1);
        for(int clan2 : clans2) sb.append("&clan2=").append(clan2);
        for(TeamLegacyUid team1 : teams1) sb.append("&team1=").append(mvcConversionService.convert(team1, String.class));
        for(TeamLegacyUid team2 : teams2) sb.append("&team2=").append(mvcConversionService.convert(team2, String.class));
        return objectMapper.readValue(mvc.perform
        (
            (
                resourceType.equals("common") || resourceType.equals("summary")
                    ? get
                    (
                        "/api/versus/" + resourceType
                            + "?type=" + mvcConversionService.convert(type, String.class) + sb
                    )
                    : get
                    (
                        "/api/versus"
                            + "/{dateAnchor}/{typeAnchor}/{mapAnchor}/{regionAnchor}"
                            + "/{page}/{pageDiff}/matches"
                            + "?type=" + mvcConversionService.convert(type, String.class) + sb,
                            dateAnchor, typeAnchor, mapAnchor, regionAnchor,
                            page, pageDiff,
                            type
                    )
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), typeReference);
    }

    private void testVersus
    (
        Tuple4<Integer[], Set<TeamLegacyUid>, Integer[], Set<TeamLegacyUid>> ids,
        Predicate<Versus> versusPredicate, Predicate<VersusSummary> versusSummaryPredicate,
        Match match1, Match match2
    )
    throws Exception
    {
        int originalPerPage = ladderMatchDAO.getResultsPerPage();
        ladderMatchDAO.setResultsPerPage(1);
        Versus versus = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            OffsetDateTime.MAX, BaseMatch.MatchType._1V1, 0, Region.US,
            0, 1,
            BaseMatch.MatchType._1V1,
            "common",
            new TypeReference<>(){}
        );
        versusPredicate.test(versus);
        versusSummaryPredicate.test(versus.getSummary());

        VersusSummary versusSummary = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            OffsetDateTime.MAX, BaseMatch.MatchType._1V1, 0, Region.US,
            0, 1,
            BaseMatch.MatchType._1V1,
            "summary",
            new TypeReference<>(){}
        );
        versusSummaryPredicate.test(versusSummary);

        PagedSearchResult<List<LadderMatch>> m1 = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            OffsetDateTime.MAX, BaseMatch.MatchType._1V1, 0, Region.US,
            0, 1,
            BaseMatch.MatchType._1V1,
            "matches",
            new TypeReference<>(){}
        );
        Match mMatch = m1.getResult().get(0).getMatch();
        assertEquals(match1, mMatch);

        PagedSearchResult<List<LadderMatch>> m2 = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            mMatch.getDate(), mMatch.getType(), mMatch.getMapId(), mMatch.getRegion(),
            1, 1,
            BaseMatch.MatchType._1V1,
            "matches",
            new TypeReference<>(){}
        );
        Match mMatch2 = m2.getResult().get(0).getMatch();
        assertEquals(match2, mMatch2);

        PagedSearchResult<List<LadderMatch>> m3 = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            mMatch2.getDate(), mMatch2.getType(), mMatch2.getMapId(), mMatch2.getRegion(),
            2, 1,
            BaseMatch.MatchType._1V1,
            "matches",
            new TypeReference<>(){}
        );
        assertTrue(m3.getResult().isEmpty());

        PagedSearchResult<List<LadderMatch>> m2Reversed = getVersus
        (
            ids.getT1(), ids.getT2(),
            ids.getT3(), ids.getT4(),
            mMatch2.getDate(), mMatch2.getType(), mMatch2.getMapId(), mMatch2.getRegion(),
            2, -1,
            BaseMatch.MatchType._1V1,
            "matches",
            new TypeReference<>(){}
        );
        Match mMatch2Reversed = m2Reversed.getResult().get(0).getMatch();
        assertEquals(match1, mMatch2Reversed);

        ladderMatchDAO.setResultsPerPage(originalPerPage);
    }

    @Test
    public void testClanVersusClan()
    throws Exception
    {
        testVersus
        (
            Tuples.of(new Integer[]{clan1.getId()}, Set.of(), new Integer[]{clan2.getId()}, Set.of()),
            versus ->
            {
                assertTrue(versus.getTeamsGroup1().isEmpty());
                assertTrue(versus.getTeamsGroup2().isEmpty());
                assertEquals(clan1, versus.getClansGroup1().get(0));
                assertEquals(clan2, versus.getClansGroup2().get(0));
                return true;
            },
            summary ->
            {
                assertEquals(2, summary.getMatches());
                assertEquals(2, summary.getWins());
                assertEquals(0, summary.getLosses());
                return true;
            },
            matches[0], matches[1]
        );
    }

    @Test
    public void testTeamVersusTeam()
    throws Exception
    {
        testVersus
        (
            Tuples.of(new Integer[0], Set.of(TeamLegacyUid.of(team2)), new Integer[0], Set.of(TeamLegacyUid.of(team3))),
            versus ->
            {
                assertTrue(versus.getClansGroup1().isEmpty());
                assertTrue(versus.getClansGroup2().isEmpty());
                assertEquals(team2, versus.getTeamsGroup1().get(0));
                assertEquals(team3, versus.getTeamsGroup2().get(0));
                return true;
            },
            summary ->
            {
                assertEquals(2, summary.getMatches());
                assertEquals(2, summary.getWins());
                assertEquals(0, summary.getLosses());
                return true;
            },
            matches[0], matches[1]
        );
    }

    @Test
    public void testTeamVersusMixed()
    throws Exception
    {
        testVersus
        (
            Tuples.of
            (
                new Integer[0], Set.of(TeamLegacyUid.of(team4)),
                new Integer[]{clan2.getId()}, Set.of(TeamLegacyUid.of(team1))
            ),
            versus ->
            {
                assertTrue(versus.getClansGroup1().isEmpty());
                assertEquals(clan2, versus.getClansGroup2().get(0));
                assertEquals(team4, versus.getTeamsGroup1().get(0));
                assertEquals(team1, versus.getTeamsGroup2().get(0));
                return true;
            },
            summary ->
            {
                assertEquals(2, summary.getMatches());
                assertEquals(0, summary.getWins());
                assertEquals(2, summary.getLosses());
                return true;
            },
            matches[3], matches[4]
        );
    }

}
