// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.ladder.*;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterReportIT
{

    @Autowired
    private PlayerCharacterReportDAO playerCharacterReportDAO;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @Autowired
    private EvidenceVoteDAO evidenceVoteDAO;

    @Autowired
    private PlayerCharacterReportService reportService;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private SC2MapDAO mapDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    public static final String BATTLETAG = "refaccount#123";

    private static Account account;
    private static MockMvc mvc;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired SeasonGenerator seasonGenerator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            account = accountDAO.merge(new Account(null, Partition.GLOBAL, BATTLETAG));
            seasonGenerator.generateDefaultSeason
            (
                List.of(Region.EU),
                List.of(BaseLeague.LeagueType.BRONZE),
                List.of(QueueType.LOTV_1V1),
                TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST, 10
            );
            mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
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
    @WithBlizzardMockUser
    (
        partition = Partition.GLOBAL,
        username = BATTLETAG,
        roles={SC2PulseAuthority.USER, SC2PulseAuthority.MODERATOR}
    )
    public void testChain()
    throws Exception
    {
        byte[] localhost = InetAddress.getByName("127.0.0.1").getAddress();
        OffsetDateTime matchDateTime = SeasonGenerator.DEFAULT_SEASON_START.atStartOfDay(ZoneId.of("UTC")).toOffsetDateTime();
        SC2Map map = mapDAO.merge(new SC2Map(null, "map"))[0];
        Match match = matchDAO.merge(new Match(null, matchDateTime, BaseMatch.MatchType._1V1, map.getId(), Region.EU))[0];
        matchParticipantDAO.merge
        (
            new MatchParticipant(match.getId(), 1L, BaseMatch.Decision.WIN),
            new MatchParticipant(match.getId(), 5L, BaseMatch.Decision.LOSS)
        );
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, matchDateTime.minusDays(10));
        playerCharacterStatsDAO.mergeCalculate();

        OffsetDateTime start = OffsetDateTime.now();

        mvc.perform(get("/api/character/report/list").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"))
            .andReturn();

        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "1")
                .param("type", "CHEATER")
                .param("evidence", "evidence text")
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "1")
                .param("additionalPlayerCharacterId", "2")
                .param("type", "LINK")
                .param("evidence", "evidence text link")
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "2")
                .param("additionalPlayerCharacterId", "3")
                .param("type", "LINK")
                .param("evidence", "evidence text link 2")
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "1")
                .param("type", "CHEATER")
                .param("evidence", "evidence text 2")
        )
        .andExpect(status().isOk())
        .andReturn();

        LadderPlayerCharacterReport[] reports = getReports();

        verifyReports(reports, new Boolean[]{null, null, null}, new Boolean[]{null, null, null, null});
        //no votes, nothing to update
        reportService.update(start);
        reports = getReports();
        verifyReports(reports, new Boolean[]{null, null, null}, new Boolean[]{null, null, null, null});

        assertEquals(2, evidenceDAO.getRequiredVotes()); //2 is a minimal value

        mvc.perform
        (
            post("/api/character/report/vote/" + reports[0].getEvidence().get(0).getEvidence().getId() + "/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/vote/" + reports[1].getEvidence().get(0).getEvidence().getId() + "/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/vote/" + reports[2].getEvidence().get(0).getEvidence().getId() + "/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        reportService.update(start);
        reports = getReports();
        //not enough votes, nothing to update
        verifyReports(reports, new Boolean[]{null, null, null}, new Boolean[]{null, null, null, null});
        assertEquals(2, evidenceDAO.getRequiredVotes());
        assertTrue(teamDAO.findCheaterTeamIds(SeasonGenerator.DEFAULT_SEASON_ID).isEmpty());

        //verify vote structure
        Arrays.stream(reports)
            .flatMap(r->r.getEvidence().stream())
            .flatMap(e->e.getVotes().stream())
            .map(LadderEvidenceVote::getVoterAccount)
            .forEach(a->assertEquals(account, a));
        LadderEvidenceVote[] votes = objectMapper.readValue(mvc.perform
        (
            post("/api/character/report/vote/" + reports[0].getEvidence().get(0).getEvidence().getId() + "/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), LadderEvidenceVote[].class);
        assertEquals(1, votes.length);
        assertEquals(reports[0].getEvidence().get(0).getVotes().get(0).getVoterAccount(), votes[0].getVoterAccount());
        assertEquals(reports[0].getEvidence().get(0).getVotes().get(0).getVote().getVote(), votes[0].getVote().getVote());

        //verify ladder status
        //verify cheater flag
        PagedSearchResult<List<LadderTeam>> teamNoFlag = ladderSearchDAO.findAnchored
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Set.of(Region.EU),
            Set.of(BaseLeague.LeagueType.BRONZE),
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            0, 99999, 0, 1
        );
        assertNull(teamNoFlag.getResult().get(9).getMembers().get(0).getConfirmedCheaterReportId());
        List<LadderDistinctCharacter> linkedCharactersNoFlag = ladderCharacterDAO
            .findLinkedDistinctCharactersByCharacterId(1L);
        //verify linked characters
        LadderDistinctCharacter cheaterCharacterNoFlag = linkedCharactersNoFlag.stream()
            .filter(c->c.getMembers().getCharacter().getId() == 1L)
            .findFirst().orElseThrow();
        assertNull(cheaterCharacterNoFlag.getMembers().getConfirmedCheaterReportId());
        assertEquals(1, linkedCharactersNoFlag.size());

        //another mod votes
        evidenceVoteDAO.merge(new EvidenceVote(
            reports[0].getEvidence().get(0).getEvidence().getId(),
            reports[0].getEvidence().get(0).getEvidence().getCreated(),
            2L, true, OffsetDateTime.now()
        ));

        //diff vote
        evidenceVoteDAO.merge(new EvidenceVote(
            reports[0].getEvidence().get(1).getEvidence().getId(),
            reports[0].getEvidence().get(1).getEvidence().getCreated(),
            2L, false, OffsetDateTime.now()
        ));

        evidenceVoteDAO.merge(new EvidenceVote(
            reports[1].getEvidence().get(0).getEvidence().getId(),
            reports[1].getEvidence().get(0).getEvidence().getCreated(),
            2L, true, OffsetDateTime.now()
        ));

        evidenceVoteDAO.merge(new EvidenceVote(
            reports[2].getEvidence().get(0).getEvidence().getId(),
            reports[2].getEvidence().get(0).getEvidence().getCreated(),
            2L, true, OffsetDateTime.now()
        ));

        reportService.update(start);
        reports = getReports();
        assertEquals(2, evidenceDAO.getActiveModCount());
        assertEquals(2, evidenceDAO.getRequiredVotes());
        //true votes confirmed the reports, but one false vote is not enough to deny it
        verifyReports(reports, new Boolean[]{true, true, true}, new Boolean[]{true, null, true, true});

        //verify ladder integration
        //verify cheater flag
        PagedSearchResult<List<LadderTeam>> teams = ladderSearchDAO.findAnchored
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Set.of(Region.EU),
            Set.of(BaseLeague.LeagueType.BRONZE),
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            0, 99999, 0, 1
        );
        assertNull(teams.getResult().get(0).getMembers().get(0).getConfirmedCheaterReportId());
        assertEquals(reports[0].getReport().getId(), teams.getResult().get(9).getMembers().get(0).getConfirmedCheaterReportId());
        //verify cheater flag for matches
        LadderMatch ladderMatch = ladderMatchDAO.findMatchesByCharacterId
        (
            1L,
            OffsetDateTime.now(),
            BaseMatch.MatchType._1V1,
            0,
            0, 1
        ).getResult().get(0);
        LadderPlayerCharacterReport[] reportsFinal = reports;
        ladderMatch.getParticipants().stream()
            .filter(p->p.getParticipant().getPlayerCharacterId() != 1L)
            .flatMap(p->p.getTeam().getMembers().stream())
            .forEach(m->assertNull(m.getConfirmedCheaterReportId()));
        ladderMatch.getParticipants().stream()
            .filter(p->p.getParticipant().getPlayerCharacterId() == 1L)
            .flatMap(p->p.getTeam().getMembers().stream())
            .forEach(m->assertEquals(reportsFinal[0].getReport().getId(), m.getConfirmedCheaterReportId()));

        List<LadderDistinctCharacter> linkedCharacters = ladderCharacterDAO
            .findLinkedDistinctCharactersByCharacterId(1L);
        //verify linked characters
        LadderDistinctCharacter cheaterCharacter = linkedCharacters.stream()
            .filter(c->c.getMembers().getCharacter().getId() == 1L)
            .findFirst().orElseThrow();
        assertEquals(reports[0].getReport().getId(), cheaterCharacter.getMembers().getConfirmedCheaterReportId());
        assertEquals(3, linkedCharacters.size());
        assertEquals(3, linkedCharacters.get(0).getMembers().getCharacter().getId());
        assertEquals(2, linkedCharacters.get(1).getMembers().getCharacter().getId());
        assertEquals(1, linkedCharacters.get(2).getMembers().getCharacter().getId());
        List<LadderDistinctCharacter> linkedCharacters3 = ladderCharacterDAO
            .findLinkedDistinctCharactersByCharacterId(3L);
        assertEquals(3, linkedCharacters3.size());
        assertEquals(3, linkedCharacters3.get(0).getMembers().getCharacter().getId());
        assertEquals(2, linkedCharacters3.get(1).getMembers().getCharacter().getId());
        assertEquals(1, linkedCharacters3.get(2).getMembers().getCharacter().getId());

        //3rd mode votes
        evidenceVoteDAO.merge(new EvidenceVote(
            reports[0].getEvidence().get(0).getEvidence().getId(),
            reports[0].getEvidence().get(0).getEvidence().getCreated(),
            3L, false, OffsetDateTime.now()
        ));

        evidenceVoteDAO.merge(new EvidenceVote(
            reports[0].getEvidence().get(1).getEvidence().getId(),
            reports[0].getEvidence().get(1).getEvidence().getCreated(),
            3L, false, OffsetDateTime.now()
        ));

        evidenceVoteDAO.merge(new EvidenceVote(
            reports[1].getEvidence().get(0).getEvidence().getId(),
            reports[1].getEvidence().get(0).getEvidence().getCreated(),
            3L, false, OffsetDateTime.now()
        ));

        evidenceVoteDAO.merge(new EvidenceVote(
            reports[2].getEvidence().get(0).getEvidence().getId(),
            reports[2].getEvidence().get(0).getEvidence().getCreated(),
            3L, false, OffsetDateTime.now()
        ));

        reportService.update(start);
        assertEquals(3, evidenceDAO.getActiveModCount());
        assertEquals(2, evidenceDAO.getRequiredVotes());
        reports = getReports();
        //one evidence is denied, but the report still has confirmed status because there is one confirmed evidence
        verifyReports(reports, new Boolean[]{true, true, true}, new Boolean[]{true, false, true, true});

        //verify find by character id
        LadderPlayerCharacterReport[] characterReports = objectMapper.readValue(mvc.perform
        (
            get("/api/character/report/list/1")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), LadderPlayerCharacterReport[].class);
        assertEquals(2, characterReports.length);
        assertEquals(reports[0].getReport().getId(), characterReports[0].getReport().getId());
        assertEquals(reports[2].getReport().getId(), characterReports[1].getReport().getId());
        assertArrayEquals
        (
            reports[0].getEvidence().stream().map(e->e.getEvidence().getId()).toArray(Integer[]::new),
            characterReports[0].getEvidence().stream().map(e->e.getEvidence().getId()).toArray(Integer[]::new)
        );
        assertArrayEquals
        (
            reports[0].getEvidence().stream().flatMap(e->e.getVotes().stream()).map(v->v.getVote().getEvidenceId()).toArray(Integer[]::new),
            characterReports[0].getEvidence().stream().flatMap(e->e.getVotes().stream()).map(v->v.getVote().getEvidenceId()).toArray(Integer[]::new)
        );
        assertArrayEquals
        (
            reports[2].getEvidence().stream().map(e->e.getEvidence().getId()).toArray(Integer[]::new),
            characterReports[1].getEvidence().stream().map(e->e.getEvidence().getId()).toArray(Integer[]::new)
        );
        assertArrayEquals
        (
            reports[2].getEvidence().stream().flatMap(e->e.getVotes().stream()).map(v->v.getVote().getEvidenceId()).toArray(Integer[]::new),
            characterReports[1].getEvidence().stream().flatMap(e->e.getVotes().stream()).map(v->v.getVote().getEvidenceId()).toArray(Integer[]::new)
        );
        assertNull(reports[0].getAdditionalMember());
        assertNull(characterReports[0].getAdditionalMember());
        assertEquals(2L, reports[2].getAdditionalMember().getCharacter().getId());
        assertEquals(2L, characterReports[1].getAdditionalMember().getCharacter().getId());

        OffsetDateTime oldTime = start.minusDays(EvidenceDAO.ACTIVE_MOD_THRESHOLD_DAYS + 1);
        PlayerCharacterReport oldReport = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 5L, null,
            PlayerCharacterReport.PlayerCharacterReportType.CHEATER, null, oldTime));
        Evidence oldEvidence = evidenceDAO.create(new Evidence(
            null, oldReport.getId(), null, localhost, "old evidence", null, oldTime, oldTime));
        evidenceVoteDAO.merge(new EvidenceVote(
            oldEvidence.getId(),
            oldEvidence.getCreated(),
            4L, false, oldTime
        ));

        reportService.update(start);
        //the vote/evidence is old, but the last unreviewed evidence after the vote is new, so the mod is counted as
        //active
        assertEquals(4, evidenceDAO.getActiveModCount());
        assertEquals(3, evidenceDAO.getRequiredVotes());

        OffsetDateTime veryOldTime = oldTime.minusDays(1);
        PlayerCharacterReport veryOldReport = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 6L, null,
            PlayerCharacterReport.PlayerCharacterReportType.CHEATER, null, veryOldTime));
        Evidence veryOldEvidence = evidenceDAO.create(new Evidence(
            null, veryOldReport.getId(), null, localhost, "very old evidence", null, veryOldTime, veryOldTime));
        evidenceVoteDAO.merge(new EvidenceVote(
            veryOldEvidence.getId(),
            veryOldEvidence.getCreated(),
            5L, false, veryOldTime
        ));

        reportService.update(start);

        //the vote/evidence is old, and the last unreviewed evidence after the vote is old too, the voter is inactive
        assertEquals(4, evidenceDAO.getActiveModCount());
        assertEquals(3, evidenceDAO.getRequiredVotes());

        evidenceVoteDAO.merge(new EvidenceVote(
            veryOldEvidence.getId(),
            veryOldEvidence.getCreated(),
            6L, false, OffsetDateTime.now()
        ));

        reportService.update(start);

        //the evidence is old, but the vote is new, the voter is considered active
        assertEquals(5, evidenceDAO.getActiveModCount());
        assertEquals(3, evidenceDAO.getRequiredVotes());

        //only confirmed evidence are taken into account here, so no errors
        for(int i = 0; i < PlayerCharacterReportService.CONFIRMED_EVIDENCE_MAX; i++)
        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "1")
                .param("type", "CHEATER")
                .param("evidence", "evidence text " + i)
        )
        .andExpect(status().isOk())
        .andReturn();

        //confirmed evidence cap test
        //fill the free spots
        for(int i = 0; i < PlayerCharacterReportService.CONFIRMED_EVIDENCE_MAX; i++)
        {
            Evidence oldEvidenceLoop = evidenceDAO.create(new Evidence(
                null, oldReport.getId(), null, localhost, "old evidence", null, oldTime, oldTime));
            evidenceVoteDAO.merge(new EvidenceVote(
                oldEvidenceLoop.getId(),
                oldEvidenceLoop.getCreated(),
                1L, true, oldTime
            ));
            evidenceVoteDAO.merge(new EvidenceVote(
                oldEvidenceLoop.getId(),
                oldEvidenceLoop.getCreated(),
                2L, true, oldTime
            ));
            evidenceVoteDAO.merge(new EvidenceVote(
                oldEvidenceLoop.getId(),
                oldEvidenceLoop.getCreated(),
                3L, true, oldTime
            ));
        }

        reportService.update(oldTime.minusDays(1));

        //and expect conflict
        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "5")
                .param("type", "CHEATER")
                .param("evidence", "evidence text ")
        )
        .andExpect(status().isConflict())
        .andReturn();

        //evidence per day cap test
        //fill the free spots
        //- 4 to offset the initial 4 evidences at the start of the test
        for(int i = 0; i < PlayerCharacterReportService.EVIDENCE_PER_DAY - PlayerCharacterReportService.CONFIRMED_EVIDENCE_MAX - 4; i++)
            mvc.perform
            (
                post("/api/character/report/new")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf().asHeader())
                    .param("playerCharacterId", "1")
                    .param("type", "CHEATER")
                    .param("evidence", "evidence text " + i)
            )
            .andExpect(status().isOk())
            .andReturn();

        //and expect too many requests
        mvc.perform
        (
            post("/api/character/report/new")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("playerCharacterId", "1")
                .param("type", "CHEATER")
                .param("evidence", "evidence text 999")
        )
        .andExpect(status().isTooManyRequests())
        .andExpect(content().json("{\"message\":\"Reports per day cap reached\"}"))
        .andReturn();

        //verify removal and hiding
        reports = getReports();
        assertEquals(5, reports.length);
        long evidenceCountEnd = Arrays.stream(reports).flatMap(r->r.getEvidence().stream()).count();
        PlayerCharacterReport expiredReport = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 8L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        PlayerCharacterReport expiredConfirmedReport = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 9L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            true, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        Evidence expiredEvidence = evidenceDAO.create(
            new Evidence(null, expiredReport.getId(), null, localhost, "description asda",
            false, OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS),OffsetDateTime.now()));
        Evidence expiredConfirmedEvidence = evidenceDAO.create(
            new Evidence(null, expiredConfirmedReport.getId(), null, localhost, "description asda",
                true, OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS),OffsetDateTime.now()));
        assertEquals(7, playerCharacterReportDAO.getAll().size());
        assertEquals(evidenceCountEnd + 2, evidenceDAO.findAll(false).size());
        reports = getReports();
        //hidden
        assertEquals(6, reports.length);
        assertEquals(evidenceCountEnd + 1, evidenceDAO.findAll(true).size());

        playerCharacterReportDAO.removeExpired();
        evidenceDAO.removeExpired();
        //expired and denied report and evidence are removed
        List<PlayerCharacterReport> endReports = playerCharacterReportDAO.getAll();
        List<Evidence> endEvidences = evidenceDAO.findAll(false);
        assertEquals(6, endReports.size());
        assertEquals(evidenceCountEnd + 1, endEvidences.size());
        //expired and confirmed report and evidence are not removed
        assertTrue(endReports.stream().anyMatch(r->r.getId().equals(expiredConfirmedReport.getId())));
        assertTrue(endEvidences.stream().anyMatch(e->e.getId().equals(expiredConfirmedEvidence.getId())));

        //cheater team is a team where at least on of its members is a cheater
        Team secondCheaterTeam = teamDAO.merge(new Team
        (
            null,
            SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("12344"), 1, 10L, 10, 0, 0, 0
        ))[0];
        teamMemberDAO.merge
        (
            new TeamMember(secondCheaterTeam.getId(), 1L, 0, 0, 0, 10),
            new TeamMember(secondCheaterTeam.getId(), 2L, 0, 0, 0, 10)
        );
        teamStateDAO.saveState(TeamState.of(secondCheaterTeam));

        //cheaters are excluded from ranking
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        leagueStatsDAO.mergeCalculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        teamStateDAO.updateRanks
        (
            SeasonGenerator.DEFAULT_SEASON_START.minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
            Set.of(SeasonGenerator.DEFAULT_SEASON_ID)
        );

        List<Long> cheaterTeams = teamDAO.findCheaterTeamIds(SeasonGenerator.DEFAULT_SEASON_ID);
        assertEquals(4, cheaterTeams.size());
        assertTrue(cheaterTeams.stream().anyMatch(t->t.equals(1L)));
        assertTrue(cheaterTeams.stream().anyMatch(t->t.equals(5L)));
        assertTrue(cheaterTeams.stream().anyMatch(t->t.equals(9L)));
        //cheater team is a team where at least on of its members is a cheater
        assertTrue(cheaterTeams.stream().anyMatch(t->t.equals(secondCheaterTeam.getId())));

        //cheaters are excluded from ranking
        Team foundSecondCheaterTeam = teamDAO.findById(secondCheaterTeam.getId()).orElseThrow();
        assertEquals(Team.DEFAULT_RANK, foundSecondCheaterTeam.getGlobalRank());
        assertEquals(Team.DEFAULT_RANK, foundSecondCheaterTeam.getRegionRank());
        assertEquals(Team.DEFAULT_RANK, foundSecondCheaterTeam.getLeagueRank());

        //cheaters are excluded from ranking
        LadderTeamState cheaterTeamState = ladderTeamStateDAO.find(1L).stream()
            .filter(s->s.getTeamState().getTeamId().equals(foundSecondCheaterTeam.getId()))
            .findAny().orElseThrow();
        assertNull(cheaterTeamState.getTeamState().getGlobalRank());
        assertNull(cheaterTeamState.getTeamState().getGlobalTeamCount());
        assertNull(cheaterTeamState.getTeamState().getRegionRank());
        assertNull(cheaterTeamState.getTeamState().getRegionTeamCount());

        LadderTeamState nonCheaterTeamState = ladderTeamStateDAO.find(2L).get(0);
        //11 team in total, 4 teams with cheater members, 7 valid teams(11 - 4)
        assertEquals(7, nonCheaterTeamState.getTeamState().getGlobalTeamCount());
        assertEquals(7, nonCheaterTeamState.getTeamState().getRegionTeamCount());
        assertEquals(7, nonCheaterTeamState.getTeamState().getGlobalRank());
        assertEquals(7, nonCheaterTeamState.getTeamState().getGlobalTeamCount());
        assertEquals(7, nonCheaterTeamState.getTeamState().getRegionRank());
        assertEquals(7, nonCheaterTeamState.getTeamState().getRegionTeamCount());
    }

    private static void verifyReports
    (LadderPlayerCharacterReport[] reports, Boolean[] expectedReportStatus, Boolean[] expectedStatus)
    throws UnknownHostException
    {
        assertEquals(3, reports.length);

        LadderPlayerCharacterReport report1 = reports[0];
        assertEquals(1, report1.getReport().getPlayerCharacterId());
        assertNull(report1.getReport().getAdditionalPlayerCharacterId());
        assertEquals(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, report1.getReport().getType());
        assertEquals(2, report1.getEvidence().size());
        assertEquals(expectedReportStatus[0], report1.getReport().getStatus());

        LadderEvidence evidence1_1 = report1.getEvidence().get(0);
        assertEquals(account.getId(), evidence1_1.getEvidence().getReporterAccountId());
        assertArrayEquals
        (
            InetAddress.getByName("127.0.0.1").getAddress(),
            evidence1_1.getEvidence().getReporterIp()
        );
        assertEquals("evidence text 2", evidence1_1.getEvidence().getDescription());
        assertEquals(expectedStatus[0], evidence1_1.getEvidence().getStatus());
        assertEquals(account, evidence1_1.getReporterAccount());

        LadderEvidence evidence1_2 = report1.getEvidence().get(1);
        assertEquals(account.getId(), evidence1_2.getEvidence().getReporterAccountId());
        assertArrayEquals
        (
            InetAddress.getByName("127.0.0.1").getAddress(),
            evidence1_2.getEvidence().getReporterIp()
        );
        assertEquals("evidence text", evidence1_2.getEvidence().getDescription());
        assertEquals(expectedStatus[1], evidence1_2.getEvidence().getStatus());
        assertEquals(account, evidence1_2.getReporterAccount());

        LadderPlayerCharacterReport report2 = reports[1];
        assertEquals(2, report2.getReport().getPlayerCharacterId());
        assertEquals(3, report2.getReport().getAdditionalPlayerCharacterId());
        assertEquals(PlayerCharacterReport.PlayerCharacterReportType.LINK, report2.getReport().getType());
        assertEquals(1, report2.getEvidence().size());
        assertEquals(expectedReportStatus[1], report2.getReport().getStatus());

        LadderEvidence evidence2_1 = report2.getEvidence().get(0);
        assertEquals(account.getId(), evidence2_1.getEvidence().getReporterAccountId());
        assertArrayEquals
        (
            InetAddress.getByName("127.0.0.1").getAddress(),
            evidence2_1.getEvidence().getReporterIp()
        );
        assertEquals("evidence text link 2", evidence2_1.getEvidence().getDescription());
        assertEquals(expectedStatus[2], evidence2_1.getEvidence().getStatus());
        assertEquals(account, evidence2_1.getReporterAccount());

        LadderPlayerCharacterReport report3 = reports[2];
        assertEquals(1, report3.getReport().getPlayerCharacterId());
        assertEquals(2, report3.getReport().getAdditionalPlayerCharacterId());
        assertEquals(PlayerCharacterReport.PlayerCharacterReportType.LINK, report3.getReport().getType());
        assertEquals(1, report3.getEvidence().size());
        assertEquals(expectedReportStatus[2], report3.getReport().getStatus());

        LadderEvidence evidence3_1 = report3.getEvidence().get(0);
        assertEquals(account.getId(), evidence3_1.getEvidence().getReporterAccountId());
        assertArrayEquals
        (
            InetAddress.getByName("127.0.0.1").getAddress(),
            evidence3_1.getEvidence().getReporterIp()
        );
        assertEquals("evidence text link", evidence3_1.getEvidence().getDescription());
        assertEquals(expectedStatus[3], evidence3_1.getEvidence().getStatus());
        assertEquals(account, evidence3_1.getReporterAccount());
    }

    private LadderPlayerCharacterReport[] getReports()
    throws Exception
    {
        return objectMapper.readValue(mvc.perform
        (
            get("/api/character/report/list")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), LadderPlayerCharacterReport[].class);
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "notamod#123")
    public void testNonModVote()
    throws Exception
    {
        mvc.perform
        (
            post("/api/character/report/vote/1/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
                .param("evidenceId", "1")
                .param("vote", "true")
        )
        .andExpect(status().isForbidden())
        .andReturn();

        mvc.perform
        (
            post("/api/character/report/vote/1/true/")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isForbidden())
        .andReturn();
    }

    @Test
    public void testReporterIpPrivacy()
    throws Exception
    {
        byte[] privateIp = InetAddress.getByName("192.168.1.2").getAddress();
        PlayerCharacterReport report = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 8L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        Evidence evidence = evidenceDAO.create(new Evidence(
            null, report.getId(), null, privateIp, "description asda",false,
            OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS) ,OffsetDateTime.now()));

        LadderPlayerCharacterReport[] reports = getReports();
        Arrays.stream(reports)
            .flatMap(r->r.getEvidence().stream())
            .forEach(e->assertArrayEquals(EvidenceDAO.REPORTER_IP_PRIVATE_REPLACEMENT, e.getEvidence().getReporterIp()));
    }

}
