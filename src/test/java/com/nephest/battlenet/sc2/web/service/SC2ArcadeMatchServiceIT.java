// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LadderUpdateDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class SC2ArcadeMatchServiceIT
{

    private static final Region ARCADE_REGION = Region.EU;
    private static final Region BLIZZARD_REGION = Region.KR;
    private static final String LADDER_MAP_NAME = "Ladder LE";
    private static final String NEXT_CURSOR = "cursor-2";
    private static final long UNKNOWN_CHARACTER = 987654321L;
    //the API returns game end times in whole seconds
    private static final ChronoUnit API_GAME_END_PRECISION =
        ChronoUnit.SECONDS;

    @Autowired private SC2ArcadeMatchService service;
    @Autowired private SC2ArcadeLadderService ladderService;
    @Autowired private MatchService matchService;
    @Autowired private SC2ArcadeLadderAPI api;
    @Autowired private MatchDAO matchDAO;
    @Autowired private MatchParticipantDAO matchParticipantDAO;
    @Autowired private PlayerCharacterDAO playerCharacterDAO;
    @Autowired private SC2MapDAO mapDAO;
    @Autowired private LadderUpdateDAO ladderUpdateDAO;
    @Autowired private SeasonGenerator seasonGenerator;
    @Autowired private JdbcTemplate template;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private final Map<String, MockResponse> responsesByCursor =
        new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<HttpUrl> requests =
        new ConcurrentLinkedDeque<>();

    private MockWebServer server;
    private WebClient originalClient;
    private OffsetDateTime now;
    private Map<Region, List<PlayerCharacter>> charactersByRegion;

    @BeforeEach
    public void beforeEach()
    throws Exception
    {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                HttpUrl url = request.getRequestUrl();
                if(!url.encodedPath().equals("/v2/regions/EU/games"))
                    return new MockResponse().setResponseCode(404);

                requests.add(url);
                String cursor = url.queryParameter("cursor");
                MockResponse response =
                    responsesByCursor.get(cursor == null ? "" : cursor);
                return response == null
                    ? createJsonResponse("{}").setResponseCode(404)
                    : response.clone();
            }
        });
        server.start();
        originalClient = api.getWebClient();
        api.setWebClient(originalClient.mutate()
            .baseUrl("http://127.0.0.1:" + server.getPort())
            .build());

        now = SC2Pulse.offsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
            .truncatedTo(API_GAME_END_PRECISION);
        resetRegions();
        service.addRegion(ARCADE_REGION);
        seasonGenerator.generateDefaultSeason
        (
            List.of(ARCADE_REGION, BLIZZARD_REGION),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            4
        );
        template.update("DELETE FROM team_state");
        charactersByRegion = playerCharacterDAO.find(new HashSet<>(
            template.queryForList(
                "SELECT id FROM player_character", Long.class)))
            .stream()
            .sorted(Comparator.comparing(PlayerCharacter::getId))
            .collect(Collectors.groupingBy(PlayerCharacter::getRegion));
        createMatch(BLIZZARD_REGION, LADDER_MAP_NAME, now.minusDays(13));
    }

    @AfterEach
    public void afterEach()
    throws Exception
    {
        resetRegions();
        api.setWebClient(originalClient);
        server.shutdown();
    }

    private void resetRegions()
    {
        for(Region region : Region.values())
        {
            if(region == Region.CN) continue;

            service.removeRegion(region);
            ladderService.removeRegion(region);
        }
    }

    private static MockResponse createJsonResponse(String body)
    {
        return new MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(body);
    }

    private void stubPage
    (
        String requestCursor,
        String nextCursor,
        boolean ready,
        String... gameJsons
    )
    {
        responsesByCursor.put(requestCursor, createJsonResponse(
            """
            {"items":[%s],"ready":%s,"navigation":{"next":%s,"hasMore":%s}}
            """.formatted
            (
                String.join(",", gameJsons),
                ready,
                nextCursor == null ? null : "\"" + nextCursor + "\"",
                nextCursor != null
            )));
    }

    private void stubPage(String... gameJsons)
    {
        stubPage("", null, true, gameJsons);
    }

    private static String createGameJson
    (
        OffsetDateTime endedAt,
        String mapName,
        Integer durationInSeconds,
        String... playerJsons
    )
    {
        return """
            {"recordId":1,"publishedAt":"2026-11-25T13:08:43.112243+00:00",
            "game":{"hash":"a","endedAt":"%s","durationSeconds":%s,
            "map":{"mapId":1,"name":%s},"players":[%s],"type":"unknown"}}
            """.formatted
            (
                endedAt,
                durationInSeconds == null
                    ? null
                    : "\"" + durationInSeconds + "\"",
                mapName == null ? null : "\"" + mapName + "\"",
                String.join(",", playerJsons)
            );
    }

    private static String createPlayerJson
    (
        int realm,
        long battlenetId,
        String result
    )
    {
        return """
            {"character":{"region":"EU","realm":"%s","toonId":"%s",
            "name":"name"},"race":"Zerg","result":"%s"}
            """.formatted(realm, battlenetId, result);
    }

    private String createPlayerJson(int characterIndex, String result)
    {
        PlayerCharacter character =
            charactersByRegion.get(ARCADE_REGION).get(characterIndex);
        return createPlayerJson
        (
            character.getRealm(), character.getBattlenetId(), result
        );
    }

    private String createLadderGameJson(OffsetDateTime endedAt)
    {
        return createGameJson
        (
            endedAt,
            LADDER_MAP_NAME,
            100,
            createPlayerJson(0, "win"),
            createPlayerJson(1, "loss")
        );
    }

    private Match createMatch
    (
        Region region,
        String mapName,
        OffsetDateTime matchDate,
        PlayerCharacter... players
    )
    {
        int mapId = mapDAO.merge(Set.of(new SC2Map(null, mapName)))
            .iterator().next().getId();
        Match match = matchDAO.merge(Set.of(new Match(
            null, matchDate, BaseMatch.MatchType._1V1, mapId, region)))
            .iterator().next();
        for(int i = 0; i < players.length; i++)
            matchParticipantDAO.merge(Set.of(new MatchParticipant
            (
                match.getId(),
                players[i].getId(),
                i == 0 ? BaseMatch.Decision.WIN : BaseMatch.Decision.LOSS
            )));
        return match;
    }

    private void createTeamState
    (
        Region region,
        int characterIndex,
        OffsetDateTime stateTimestamp
    )
    {
        template.update
        (
            """
            INSERT INTO team_state
            (team_id, "timestamp", division_id, games, rating, region_rank)
            SELECT team.id, ?, team.division_id, 1, 1, 1
            FROM team
            INNER JOIN team_member ON team.id = team_member.team_id
            WHERE player_character_id = ?
            """,
            stateTimestamp,
            charactersByRegion.get(region).get(characterIndex).getId()
        );
    }

    private void createTeamStates
    (
        Region region,
        OffsetDateTime stateTimestamp
    )
    {
        createTeamState(region, 0, stateTimestamp);
        createTeamState(region, 1, stateTimestamp);
    }

    private Map<Region, Set<PlayerCharacter>> updateMatches()
    {
        requests.clear();
        return service.update().block();
    }

    private void updateMatchMeta()
    {
        matchService.updateMeta(
            new UpdateContext(now.toInstant(), now.toInstant()));
    }

    private List<LadderMatch> findMatches()
    throws Exception
    {
        String characterIds = charactersByRegion.values().stream()
            .flatMap(List::stream)
            .map(character->String.valueOf(character.getId()))
            .collect(Collectors.joining(","));
        return WebServiceTestUtil.getObject
        (
            mvc,
            objectMapper,
            new TypeReference<CursorNavigableResult<List<LadderMatch>>>(){},
            "/api/character-matches?limit=100&characterId=" + characterIds
        ).result();
    }

    private int countArcadeRegionMatches()
    throws Exception
    {
        return (int) findMatches().stream()
            .filter(match->match.getMatch().getRegion() == ARCADE_REGION)
            .count();
    }

    private LadderMatch findMatch(OffsetDateTime matchDate)
    throws Exception
    {
        return findMatches().stream()
            .filter(match->match.getMatch().getDate().isEqual(matchDate))
            .findAny()
            .orElseThrow();
    }

    private int countParticipants(OffsetDateTime matchDate, boolean tied)
    throws Exception
    {
        return (int) findMatch(matchDate).getParticipants().stream()
            .filter(participant->
                (participant.getParticipant().getTeamId() != null) == tied)
            .count();
    }

    private Integer findMatchDuration(OffsetDateTime matchDate)
    throws Exception
    {
        return findMatch(matchDate).getMatch().getDuration();
    }

    private Map<Long, MatchParticipant> findParticipants
    (OffsetDateTime matchDate)
    throws Exception
    {
        return findMatch(matchDate).getParticipants().stream()
            .map(LadderMatchParticipant::getParticipant)
            .collect(Collectors.toMap(
                MatchParticipant::getPlayerCharacterId,
                Function.identity()
            ));
    }

    private Instant getRequestInstant(String parameterName)
    {
        return SC2Pulse.instant(Instant.parse(
            requests.getFirst().queryParameter(parameterName)));
    }

    @Test
    public void whenUpdating_thenSaveOnly1v1LadderMatches()
    throws Exception
    {
        OffsetDateTime end = now.minusHours(1);
        String win = createPlayerJson(0, "win");
        String loss = createPlayerJson(1, "loss");
        stubPage
        (
            createGameJson(end, LADDER_MAP_NAME, 321, win, loss),
            createGameJson(end.plusSeconds(1), "Custom", 1, win, loss),
            createGameJson
            (
                end.plusSeconds(3),
                LADDER_MAP_NAME,
                1,
                win,
                createPlayerJson(1, "win")
            ),
            createGameJson
            (
                end.plusSeconds(4),
                LADDER_MAP_NAME,
                1,
                win,
                loss,
                createPlayerJson(2, "win"),
                createPlayerJson(3, "loss")
            ),
            createGameJson(end.plusSeconds(5), LADDER_MAP_NAME, 1, win),
            createGameJson
            (
                end.plusSeconds(6),
                LADDER_MAP_NAME,
                1,
                win,
                createPlayerJson(1, "bailer")
            ),
            createGameJson(end.plusSeconds(7), null, 1, win, loss)
        );
        for(int i = 0; i < 2; i++)
        {
            assertEquals
            (
                Map.of(ARCADE_REGION, Set.copyOf(
                    charactersByRegion.get(ARCADE_REGION).subList(0, 2))),
                updateMatches()
            );
            assertEquals(1, countArcadeRegionMatches());
            assertEquals(321, findMatchDuration(end));
            Map<Long, BaseMatch.Decision> decisions = findParticipants(end)
                .values()
                .stream()
                .collect(Collectors.toMap(
                    MatchParticipant::getPlayerCharacterId,
                    MatchParticipant::getDecision
                ));
            assertEquals
            (
                Map.of
                (
                    charactersByRegion.get(ARCADE_REGION).get(0).getId(),
                    BaseMatch.Decision.WIN,
                    charactersByRegion.get(ARCADE_REGION).get(1).getId(),
                    BaseMatch.Decision.LOSS
                ),
                decisions
            );
        }
    }

    @Test
    public void whenUnknownCharacter_thenSaveKnownCharacters()
    throws Exception
    {
        OffsetDateTime end = now.minusHours(1);
        stubPage
        (
            createGameJson
            (
                end,
                LADDER_MAP_NAME,
                null,
                createPlayerJson(0, "tie"),
                createPlayerJson(1, UNKNOWN_CHARACTER, "tie")
            ),
            createGameJson(end.plusSeconds(1), LADDER_MAP_NAME, 1,
                createPlayerJson(1, UNKNOWN_CHARACTER, "win"),
                createPlayerJson(1, UNKNOWN_CHARACTER + 1, "loss"))
        );
        updateMatches();
        assertEquals(1, countArcadeRegionMatches());
        assertEquals(1, countParticipants(end, false));
        assertNull(findMatchDuration(end));
    }

    @Test
    public void whenGameIsPublishedLate_thenSaveIt()
    throws Exception
    {
        OffsetDateTime end = now.minusHours(1);
        stubPage(createLadderGameJson(end));
        updateMatches();
        assertEquals(1, countArcadeRegionMatches());

        stubPage
        (
            createLadderGameJson(end.minusHours(10)),
            createLadderGameJson(end)
        );
        Instant start = SC2Pulse.instant();
        updateMatches();
        assertEquals(2, countArcadeRegionMatches());
        assertTrue(!getRequestInstant("to").isBefore(start));
        assertEquals
        (
            getRequestInstant("to").minus(SC2ArcadeMatchService.MATCH_LOOKBACK),
            getRequestInstant("from")
        );
    }

    @Test
    public void whenPageFails_thenKeepSavedPagesAndReadAgain()
    throws Exception
    {
        stubPage
        (
            "",
            NEXT_CURSOR,
            true,
            createLadderGameJson(now.minusHours(2))
        );
        for(MockResponse response : List.of(
            createJsonResponse("{}").setResponseCode(410),
            createJsonResponse("{}").setResponseCode(400),
            new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "text/html")
                .setBody("login")))
        {
            responsesByCursor.put(NEXT_CURSOR, response);
            updateMatches();
            assertEquals(2, requests.size());
            assertEquals(1, countArcadeRegionMatches());
        }

        stubPage
        (
            NEXT_CURSOR,
            null,
            true,
            createLadderGameJson(now.minusHours(1))
        );
        updateMatches();
        assertEquals(2, countArcadeRegionMatches());
    }

    @Test
    public void whenCursorIsRepeated_thenStop()
    throws Exception
    {
        stubPage
        (
            "",
            NEXT_CURSOR,
            true,
            createLadderGameJson(now.minusHours(2))
        );
        stubPage
        (
            NEXT_CURSOR,
            NEXT_CURSOR,
            true,
            createLadderGameJson(now.minusHours(1))
        );
        updateMatches();
        assertEquals(2, requests.size());
        assertEquals(2, countArcadeRegionMatches());
    }

    @Test
    public void whenSaveFails_thenRollbackPageAndReadAgain()
    throws Exception
    {
        stubPage
        (
            "",
            NEXT_CURSOR,
            true,
            createLadderGameJson(now.minusHours(2))
        );
        stubPage
        (
            NEXT_CURSOR, null, true,
            createLadderGameJson(now.minusHours(1)),
            createGameJson
            (
                now,
                LADDER_MAP_NAME,
                5000,
                createPlayerJson(2, "win"),
                createPlayerJson(3, "loss")
            )
        );
        //the participants are saved after matches
        long failedCharacterId =
            charactersByRegion.get(ARCADE_REGION).get(2).getId();
        template.execute("ALTER TABLE match_participant ADD CONSTRAINT "
            + "ck_test CHECK(player_character_id != "
            + failedCharacterId + ")");
        try
        {
            updateMatches();
        }
        finally
        {
            template.execute("ALTER TABLE match_participant "
                + "DROP CONSTRAINT ck_test");
        }
        assertEquals(1, countArcadeRegionMatches());

        updateMatches();
        assertEquals(3, countArcadeRegionMatches());
    }

    @CsvSource(nullValues = "null", value =
    {
        "null, null", "-1, null", "0, 0", "32767, 32767", "32768, null"
    })
    @ParameterizedTest
    public void testDuration
    (
        Integer apiDurationInSeconds,
        Integer expectedDurationInSeconds
    )
    throws Exception
    {
        OffsetDateTime end = now.minusHours(1);
        stubPage
        (
            createGameJson
            (
                end,
                LADDER_MAP_NAME,
                apiDurationInSeconds,
                createPlayerJson(0, "win"),
                createPlayerJson(1, "loss")
            )
        );
        updateMatches();
        assertEquals(expectedDurationInSeconds, findMatchDuration(end));
    }

    @Test
    public void whenNotReady_thenSaveNothing()
    throws Exception
    {
        stubPage("", null, false, createLadderGameJson(now.minusHours(1)));
        updateMatches();
        assertEquals(1, requests.size());
        assertEquals(0, countArcadeRegionMatches());
    }

    @Test
    public void whenMapPoolIsEmpty_thenSaveNothing()
    throws Exception
    {
        stubPage(createLadderGameJson(now.minusHours(1)));
        //move the only map pool match out of the map pool frame
        template.update
        (
            "UPDATE match SET date = ?",
            now.minus(SC2ArcadeMatchService.MAP_POOL_MATCH_FRAME)
                .minusSeconds(1)
        );
        updateMatches();
        assertTrue(requests.isEmpty());
        assertEquals(0, countArcadeRegionMatches());
    }

    @Test
    public void whenLateMatch_thenTieOnlyWithinLookback()
    throws Exception
    {
        OffsetDateTime lateMatchDate = now.minusHours(10);
        OffsetDateTime tooLateMatchDate = now.minusHours(25);
        stubPage(createLadderGameJson(lateMatchDate));
        createMatch
        (
            ARCADE_REGION,
            LADDER_MAP_NAME,
            tooLateMatchDate,
            charactersByRegion.get(ARCADE_REGION).get(0),
            charactersByRegion.get(ARCADE_REGION).get(1)
        );
        createTeamStates(ARCADE_REGION, tooLateMatchDate.plusMinutes(5));
        createTeamStates(ARCADE_REGION, lateMatchDate.plusMinutes(5));
        updateMatches();

        List<PlayerCharacter> blizzardCharacters =
            charactersByRegion.get(BLIZZARD_REGION);
        OffsetDateTime oldBlizzardMatchDate = now.minusHours(9);
        OffsetDateTime newBlizzardMatchDate = now.minusMinutes(30);
        for
        (
            OffsetDateTime matchDate
                : List.of(oldBlizzardMatchDate, newBlizzardMatchDate)
        )
        {
            createMatch
            (
                BLIZZARD_REGION,
                LADDER_MAP_NAME,
                matchDate,
                blizzardCharacters.get(0),
                blizzardCharacters.get(1)
            );
            createTeamStates(BLIZZARD_REGION, matchDate.plusMinutes(5));
        }

        updateMatchMeta();
        assertEquals(2, countParticipants(lateMatchDate, true));
        assertEquals(2, countParticipants(tooLateMatchDate, false));
        assertEquals(2, countParticipants(oldBlizzardMatchDate, false));
        assertEquals(2, countParticipants(newBlizzardMatchDate, true));
    }

    @Test
    public void whenArcadeMatch_thenKeepMeasuredDuration()
    throws Exception
    {
        int secondsBetweenMatchEnds = 300;
        int estimatedDurationInSeconds =
            secondsBetweenMatchEnds - MatchDAO.DURATION_OFFSET;
        //realistic, but longer than the estimate, so the estimate would win
        int measuredDurationInSeconds = estimatedDurationInSeconds + 20;
        assertTrue(measuredDurationInSeconds < secondsBetweenMatchEnds);
        OffsetDateTime first = now.minusMinutes(40);
        OffsetDateTime second = first.plusSeconds(secondsBetweenMatchEnds);
        stubPage
        (
            createLadderGameJson(first),
            createGameJson
            (
                second,
                LADDER_MAP_NAME,
                measuredDurationInSeconds,
                createPlayerJson(0, "win"),
                createPlayerJson(1, "loss")
            )
        );
        updateMatches();
        OffsetDateTime firstBlizzardMatchDate = now.minusMinutes(20);
        OffsetDateTime secondBlizzardMatchDate =
            firstBlizzardMatchDate.plusSeconds(secondsBetweenMatchEnds);
        for
        (
            OffsetDateTime matchDate
                : List.of(firstBlizzardMatchDate, secondBlizzardMatchDate)
        )
            createMatch
            (
                BLIZZARD_REGION,
                LADDER_MAP_NAME,
                matchDate,
                charactersByRegion.get(BLIZZARD_REGION).get(0),
                charactersByRegion.get(BLIZZARD_REGION).get(1)
            );

        updateMatchMeta();
        assertEquals(measuredDurationInSeconds, findMatchDuration(second));
        assertEquals
        (
            estimatedDurationInSeconds,
            findMatchDuration(secondBlizzardMatchDate)
        );
    }

    @CsvSource
    ({
        "true, true, 30, true",
        "true, false, 30, true",
        "false, true, 30, false",
        "false, false, 30, false",
        "false, false, 2, true",
        "true, true, 61, false",
        "false, false, 61, false"
    })
    @ParameterizedTest
    public void testTieLimit
    (
        boolean arcadeLadder,
        boolean arcadeMatches,
        int stateOffsetInMinutes,
        boolean expectedTied
    )
    throws Exception
    {
        if(arcadeLadder) ladderService.addRegion(ARCADE_REGION);
        if(!arcadeMatches) service.removeRegion(ARCADE_REGION);
        OffsetDateTime matchDate = now.minusMinutes(50);
        createMatch
        (
            ARCADE_REGION,
            LADDER_MAP_NAME,
            matchDate,
            charactersByRegion.get(ARCADE_REGION).get(0),
            charactersByRegion.get(ARCADE_REGION).get(1)
        );
        createTeamStates
        (
            ARCADE_REGION,
            matchDate.plusMinutes(stateOffsetInMinutes)
        );
        //the limit is 4 minutes
        ladderUpdateDAO.create(Set.of(new LadderUpdate
        (
            ARCADE_REGION,
            QueueType.LOTV_1V1,
            BaseLeague.LeagueType.BRONZE,
            now,
            Duration.ofMinutes(2)
        )));

        updateMatchMeta();
        assertEquals(2, countParticipants(matchDate, expectedTied));
    }

    @Test
    public void whenRatingChanges_thenCalculateItWithinLookback()
    throws Exception
    {
        OffsetDateTime first = now.minusHours(10);
        OffsetDateTime second = first.plusHours(2);
        stubPage(createLadderGameJson(first), createLadderGameJson(second));
        OffsetDateTime firstStateTimestamp = first.plusMinutes(5);
        OffsetDateTime secondStateTimestamp = second.plusMinutes(5);
        createTeamStates(ARCADE_REGION, firstStateTimestamp);
        createTeamStates(ARCADE_REGION, secondStateTimestamp);
        //the first character wins, the second one loses
        updateRating(0, firstStateTimestamp, 100);
        updateRating(1, firstStateTimestamp, 100);
        updateRating(0, secondStateTimestamp, 120);
        updateRating(1, secondStateTimestamp, 80);
        updateMatches();
        updateMatchMeta();

        Map<Long, MatchParticipant> participants = findParticipants(second);
        List<PlayerCharacter> characters =
            charactersByRegion.get(ARCADE_REGION);
        assertEquals
        (
            20,
            participants.get(characters.get(0).getId()).getRatingChange()
        );
        assertEquals
        (
            -20,
            participants.get(characters.get(1).getId()).getRatingChange()
        );
    }

    private void updateRating
    (
        int characterIndex,
        OffsetDateTime stateTimestamp,
        int rating
    )
    {
        template.update
        (
            """
            UPDATE team_state
            SET rating = ?
            FROM team_member
            WHERE team_state.team_id = team_member.team_id
            AND team_member.player_character_id = ?
            AND team_state."timestamp" = ?
            """,
            rating,
            charactersByRegion.get(ARCADE_REGION).get(characterIndex).getId(),
            stateTimestamp
        );
    }

}
