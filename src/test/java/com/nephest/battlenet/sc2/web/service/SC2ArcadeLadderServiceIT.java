// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.FastTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderCharacter;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.web.SeasonService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Scheduler;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class SC2ArcadeLadderServiceIT
{

    private static final Region REGION = Region.CN;
    private static final int CN_SEASON = 60;
    private static final int PULSE_SEASON = 69;
    private static final String PREFIX = "/v2/regions/CN";
    private static final String CURSOR = "cursor-2";
    private static final String ONE = "1v1ARRANGED";
    private static final String TWO = "2v2RANDOM";
    private static final String THREE = "3v3RANDOM";
    private static final String FOUR = "4v4RANDOM";
    //exceeds signed 64 bits
    private static final String TEAM_1 = "14305739282118082560";
    private static final String TEAM_2 = "14585807983456813056";
    private static final OffsetDateTime NOW = SC2Pulse.offsetDateTime();
    private static final OffsetDateTime SEASON_START = NOW.minusDays(14);
    private static final OffsetDateTime JOINED = NOW.minusDays(5);
    private static final OffsetDateTime OBSERVED = NOW.minusHours(2);

    @Autowired private SC2ArcadeLadderService service;
    @Autowired private SC2ArcadeLadderAPI api;
    @Autowired private JdbcTemplate template;
    @Autowired private SeasonDAO seasonDAO;
    @Autowired private LeagueDAO leagueDAO;
    @Autowired private LeagueTierDAO leagueTierDAO;
    @Autowired private DivisionDAO divisionDAO;
    @Autowired private TeamDAO teamDAO;
    @Autowired private FastTeamDAO fastTeamDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private PlayerCharacterDAO playerCharacterDAO;
    @Autowired private TeamMemberDAO teamMemberDAO;
    @Autowired private ClanDAO clanDAO;
    @Autowired private ClanMemberDAO clanMemberDAO;
    @Autowired private VarDAO varDAO;
    @Autowired private SeasonService seasonService;
    @Autowired private Validator validator;
    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;
    @Autowired @Qualifier("dbExecutorService")
    private ExecutorService dbExecutorService;
    @Autowired @Qualifier("secondaryDbScheduler")
    private Scheduler secondaryDbScheduler;
    @Autowired @Qualifier("webExecutorService")
    private ExecutorService webExecutorService;

    private final Map<String, MockResponse> responses =
        new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> requests =
        new ConcurrentLinkedQueue<>();

    private MockWebServer server;
    private WebClient originalClient;
    private BlizzardSC2API blizzardApi;
    private SC2WebServiceUtil webServiceUtil;
    private AlternativeLadderService alternativeLadderService;
    private ClanService clanService;
    private StatsService statsService;

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
                if(!request.getPath().startsWith(PREFIX))
                    return json("{}").setResponseCode(404);

                String key = key(request.getRequestUrl());
                requests.add(key);
                MockResponse response = responses.get(key);
                return response != null
                    ? response.clone()
                    : json("{\"status\":404}").setResponseCode(404);
            }
        });
        server.start();
        originalClient = api.getWebClient();
        api.setWebClient(originalClient.mutate()
            .baseUrl("http://127.0.0.1:" + server.getPort())
            .build());

        for(Region region : Region.values())
            if(region != REGION) service.removeRegion(region);
        service.clearMemberCache();
        service.copyAndClearPendingData();
        service.getMemberRefreshInstant(REGION).setValue(null);
        fastTeamDAO.clear(REGION);
        statsService = createStatsService();
        stubDefaults();
    }

    @AfterEach
    public void afterEach()
    throws Exception
    {
        fastTeamDAO.clear(REGION);
        api.setWebClient(originalClient);
        server.shutdown();
    }

    private StatsService createStatsService()
    {
        blizzardApi = mock(BlizzardSC2API.class);
        webServiceUtil = mock(SC2WebServiceUtil.class);
        alternativeLadderService = mock(AlternativeLadderService.class);
        clanService = mock(ClanService.class);
        StatsService result = new StatsService
        (
            alternativeLadderService,
            service,
            blizzardApi,
            seasonDAO,
            leagueDAO,
            leagueTierDAO,
            divisionDAO,
            teamDAO,
            fastTeamDAO,
            mock(TeamStateDAO.class),
            mock(TeamHistoryDAO.class),
            accountDAO,
            playerCharacterDAO,
            teamMemberDAO,
            mock(QueueStatsDAO.class),
            mock(LeagueStatsDAO.class),
            mock(PopulationStateDAO.class),
            varDAO,
            seasonService,
            webServiceUtil,
            conversionService,
            validator,
            dbExecutorService,
            clanService,
            mock(EventService.class)
        );
        result.init();
        return result;
    }

    private static String key(HttpUrl url)
    {
        String queue = url.queryParameter("queue");
        String cursor = url.queryParameter("cursor");
        return url.encodedPath().substring(PREFIX.length())
            + (queue == null
                ? ""
                : "?" + queue + url.queryParameter("teamType"))
            + (cursor == null ? "" : "#" + cursor);
    }

    private static MockResponse json(String body)
    {
        return new MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(body);
    }

    private void stub(String key, String body)
    {
        responses.put(key, json(body));
    }

    private void stubDefaults()
    {
        stub("/coverage", """
            {"region":"CN","season":60,"coverage":
            {"knownDivisions":353,"oldestStandingsCheckAt":"%s"}}
            """.formatted(OBSERVED));
        stubSeason(CN_SEASON, 4, SEASON_START, NOW.plusDays(50));
        stub("/queues", page(Arrays.stream(QueueType.values())
            .filter(q->q.getVersion() == StatsService.VERSION)
            .flatMap(q->Arrays.stream(TeamType.values())
                .map(t->"{\"queue\":\"x\",\"queueId\":%s,\"teamType\":\"%s\"}"
                    .formatted(q.getId(), t)))
            .toArray(String[]::new)));
        for(String pair : List.of(ONE, TWO, THREE, FOUR))
        {
            stub("/divisions?" + pair, page());
            stub("/rankings?" + pair, page());
            stub("/tier-thresholds?" + pair, page());
        }
    }

    private void stubSeason
    (
        int id,
        int number,
        OffsetDateTime start,
        OffsetDateTime end
    )
    {
        stub("/seasons/current", """
            {"season":%s,"year":2026,"number":%s,"startsAt":"%s","endsAt":"%s"}
            """.formatted(id, number, start, end));
    }

    private static String page(String... items)
    {
        return "{\"items\":[" + String.join(",", items)
            + "],\"navigation\":{\"next\":null,\"hasMore\":false}}";
    }

    private static String nextPage(String... items)
    {
        return "{\"items\":[" + String.join(",", items)
            + "],\"navigation\":{\"next\":\"" + CURSOR
            + "\",\"hasMore\":true}}";
    }

    private static String division(long id, LeagueType league, int tier)
    {
        return """
            {"ladderId":"%s","league":"%s","tier":%s,"reportedMembers":100}
            """.formatted(id, league, tier);
    }

    private static String row(String teamId, String race, int wins)
    {
        return row(teamId, race, wins, JOINED);
    }

    private static String row
    (
        String teamId,
        String race,
        int wins,
        OffsetDateTime joined
    )
    {
        return """
            {"teamId":"%s","race":%s,"mmr":"3316","wins":%s,"losses":1,
            "points":269.6,"rank":1,"joinedAt":"%s"}
            """.formatted
            (
                teamId,
                race == null ? null : "\"" + race + "\"",
                wins,
                joined
            );
    }

    private static String member(long id, String name)
    {
        return """
            {"region":"CN","realm":"1","toonId":"%s","name":%s,
            "nameResolutionStatus":"%s"}
            """.formatted
            (
                id,
                name == null ? null : "\"" + name + "\"",
                name == null ? "pending" : "resolved"
            );
    }

    private static String ranking(String teamId, String... members)
    {
        return "{\"teamId\":\"" + teamId + "\",\"members\":["
            + String.join(",", members) + "]}";
    }

    private void stubRoster(long id, String... rows)
    {
        stubRoster(id, CN_SEASON, OBSERVED, OBSERVED, rows);
    }

    private void stubRoster
    (
        long id,
        int season,
        OffsetDateTime observed,
        OffsetDateTime checked,
        String... rows
    )
    {
        stub("/ladders/" + id, """
            {"snapshot":{"season":%s,"observedAt":%s,"lastCheckedAt":"%s"},
            "items":[%s],"navigation":{"next":null,"hasMore":false}}
            """.formatted
            (
                season,
                observed == null ? null : "\"" + observed + "\"",
                checked,
                String.join(",", rows)
            ));
    }

    private void stub1v1(String... rows)
    {
        stub("/divisions?" + ONE, page(division(1, LeagueType.DIAMOND, 1)));
        stubRoster(1, rows);
    }

    private void stubRandom(String race, int wins)
    {
        stub("/divisions?" + TWO, page(division(2, LeagueType.GOLD, 2)));
        stubRoster(2, row(TEAM_1, race, wins));
        stub("/rankings?" + TWO, page(ranking(TEAM_1, member(1, "name1#11"))));
    }

    private void stub1v1Team(int wins, String name)
    {
        stub1v1(row(TEAM_1, "Zerg", wins));
        stub("/rankings?" + ONE, page(ranking(TEAM_1, member(1, name))));
    }

    private void scan()
    throws Exception
    {
        scan(LadderUpdateContext.ALL);
    }

    private void scan(Map<QueueType, Set<LeagueType>> data)
    throws Exception
    {
        requests.clear();
        for(Future<Void> task : statsService
            .updateCurrent(Map.of(REGION, data), false)
            .get(REGION).getTasks()) task.get();
    }

    /** next snapshot of the division 1 */
    private void stubNextRoster(String... rows)
    {
        stubRoster
        (
            1, CN_SEASON, OBSERVED.plusHours(1), OBSERVED.plusHours(1), rows
        );
    }

    private Map<String, Object> team()
    {
        return template.queryForMap("SELECT * FROM team");
    }

    private int count(String table)
    {
        return template.queryForObject(
            "SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String name(long battlenetId)
    {
        return template.queryForObject
        (
            "SELECT name FROM player_character WHERE battlenet_id = ?",
            String.class,
            battlenetId
        );
    }

    private OffsetDateTime odt(String query)
    {
        return template.queryForObject(query, OffsetDateTime.class);
    }

    private static void assertSameInstant
    (
        OffsetDateTime expected,
        OffsetDateTime actual
    )
    {
        assertTrue(expected.isEqual(actual), expected + " " + actual);
    }

    private long rankingRequests()
    {
        return requests.stream().filter(r->r.startsWith("/rankings")).count();
    }

    @Test
    public void whenFullScan_thenSaveAllRows()
    throws Exception
    {
        stub("/divisions?" + ONE, nextPage(division(1, LeagueType.DIAMOND, 1)));
        stub("/divisions?" + ONE + "#" + CURSOR,
            page(division(3, LeagueType.GRANDMASTER, 0)));
        stub("/ladders/1", """
            {"snapshot":{"season":60,"observedAt":"%s","lastCheckedAt":"%s"},
            "items":[%s],"navigation":{"next":"%s","hasMore":true}}
            """.formatted(OBSERVED, OBSERVED.plusMinutes(1),
                row(TEAM_1, "Zerg", 10), CURSOR));
        stub("/ladders/1#" + CURSOR, """
            {"snapshot":{"season":60,"observedAt":"%s","lastCheckedAt":"%s"},
            "items":[%s],"navigation":{"next":null,"hasMore":false}}
            """.formatted(OBSERVED, OBSERVED, row(TEAM_2, "Terran", 5)));
        stubRoster(3);
        //one character, two races
        stub("/rankings?" + ONE, nextPage(
            ranking(TEAM_1, member(1, "name1#11"))));
        stub("/rankings?" + ONE + "#" + CURSOR, page(
            ranking(TEAM_2, member(1, "name1#11"))));

        scan();
        assertEquals(2, count("team"));
        assertEquals(1, count("player_character"));
        assertEquals("name1#11", name(1));
        assertEquals("f#511", template.queryForObject(
            "SELECT battle_tag FROM account", String.class));
        Season season = seasonDAO.find(REGION, CN_SEASON).orElseThrow();
        assertEquals(4, season.getNumber());

        Map<String, Object> team = template.queryForMap(
            "SELECT * FROM team WHERE legacy_id = '1.1.3'");
        assertEquals(season.getPulseSeasonId(),
            ((Number) team.get("season")).intValue());
        assertEquals(LeagueType.DIAMOND.getId(),
            ((Number) team.get("league_type")).intValue());
        assertEquals(LeagueTierType.SECOND.getId(),
            ((Number) team.get("tier_type")).intValue());
        assertEquals(3316, ((Number) team.get("rating")).intValue());
        assertEquals(10, ((Number) team.get("wins")).intValue());
        assertEquals(1, ((Number) team.get("losses")).intValue());
        assertEquals(0, ((Number) team.get("ties")).intValue());
        assertEquals(270, ((Number) team.get("points")).intValue());
        String fields = " FROM team WHERE legacy_id = '1.1.3'";
        assertSameInstant(OBSERVED, odt("SELECT last_played" + fields));
        assertSameInstant(JOINED, odt("SELECT joined" + fields));
        assertSameInstant(OBSERVED.plusMinutes(1),
            odt("SELECT primary_data_updated" + fields));
        assertEquals(11, template.queryForObject
        (
            """
                SELECT zerg_games_played
                FROM team_member
                WHERE zerg_games_played IS NOT NULL
                AND terran_games_played IS NULL
                AND protoss_games_played IS NULL
                AND random_games_played IS NULL
            """,
            Integer.class
        ));
        assertEquals(2, count("team_member"));
        assertEquals(LeagueTierType.FIRST.getId(), template.queryForObject
        (
            """
                SELECT league_tier.type
                FROM division
                INNER JOIN league_tier ON league_tier_id = league_tier.id
                WHERE battlenet_id = 3
            """,
            Integer.class
        ));

        PendingLadderData pending = service.copyAndClearPendingData();
        assertEquals(2, pending.getTeams().size());
        assertEquals(1, pending.getCharacters().size());
        verifyNoInteractions(blizzardApi, webServiceUtil, clanService);
        verify(alternativeLadderService, never()).updateSeason(any(), any());

        //nothing was changed, so there is nothing to update
        List<Map<String, Object>> teams =
            template.queryForList("SELECT * FROM team ORDER BY id");
        scan();
        assertEquals(teams,
            template.queryForList("SELECT * FROM team ORDER BY id"));
        assertTrue(service.copyAndClearPendingData().getTeams().isEmpty());
        assertEquals(0, rankingRequests());
    }

    @Test
    public void whenOffsetSeasonAlreadyExists_thenUsePulseSeasonAndUpdateDates()
    throws Exception
    {
        OffsetDateTime start = SEASON_START.plusHours(1);
        seasonDAO.create(new Season(null, PULSE_SEASON, Region.EU, 2026, 4,
            start, start.plusDays(60)));
        seasonDAO.create(new Season(null, CN_SEASON, REGION, 2026, 4,
            start, start.plusDays(60)));
        stub1v1Team(10, "name1#11");
        scan();
        assertEquals(PULSE_SEASON, ((Number) team().get("season")).intValue());
        assertSameInstant(SEASON_START,
            seasonDAO.find(REGION, CN_SEASON).orElseThrow().getStart());
    }

    @Test
    public void whenSeasonHasEnded_thenKeepUpdatingIt()
    throws Exception
    {
        stubSeason(CN_SEASON, 4, SEASON_START, NOW.minusDays(1));
        stub1v1Team(10, "name1#11");
        scan();
        stubNextRoster(row(TEAM_1, "Zerg", 11));
        scan();
        assertEquals(11, ((Number) team().get("wins")).intValue());
    }

    @Test
    public void whenNewSeason_thenClearMemberCache()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        int oldSeason = ((Number) team().get("season")).intValue();
        assertEquals(1, service.getCachedCharacters(REGION, oldSeason).size());

        stubSeason(CN_SEASON + 1, 5, NOW.minusHours(3), NOW.plusDays(50));
        stub("/rankings?" + ONE, page());
        scan();
        assertTrue(service.getCachedCharacters(REGION, oldSeason).isEmpty());
        assertEquals(4, rankingRequests());
        //the roster belongs to the old season
        assertEquals(1, count("team"));
    }

    @Test
    public void whenRosterIsStale_thenDontOverwrite()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        stubRoster(1, CN_SEASON, OBSERVED.plusHours(1), OBSERVED.minusHours(1),
            row(TEAM_1, "Zerg", 9));
        scan();
        assertEquals(10, ((Number) team().get("wins")).intValue());
    }

    @Test
    public void whenSameTeamIdInRandomQueues_thenSaveSeparateTeams()
    throws Exception
    {
        long ladderId = 1;
        for(String pair : List.of(TWO, THREE, FOUR))
        {
            stub("/divisions?" + pair,
                page(division(ladderId, LeagueType.GOLD, 0)));
            stubRoster(ladderId, row(TEAM_1, "Zerg", 10));
            ladderId++;
        }
        //rankings can lose teams, so it's present only in a single queue
        stub("/rankings?" + FOUR, page(ranking(TEAM_1, member(1, "name1#11"))));
        //partial update reads all rankings too
        scan(Map.of
        (
            QueueType.LOTV_2V2, LadderUpdateContext.ALL_LEAGUES,
            QueueType.LOTV_3V3, LadderUpdateContext.ALL_LEAGUES
        ));
        assertEquals(2, count("team"));
        scan();
        assertEquals
        (
            List.of(202, 203, 204),
            template.queryForList
            (
                "SELECT queue_type FROM team "
                    + "WHERE legacy_id = '1.1.' AND team_type = 1 "
                    + "ORDER BY queue_type",
                Integer.class
            )
        );
        assertEquals(1, count("player_character"));
    }

    @Test
    public void whenOutOfScope_thenDontRequest()
    throws Exception
    {
        scan();
        assertEquals
        (
            Set.of(ONE, TWO, THREE, FOUR),
            requests.stream()
                .filter(r->r.contains("?"))
                .map(r->r.substring(r.indexOf('?') + 1))
                .collect(Collectors.toSet())
        );
    }

    @Test
    public void whenQueueIsNotAdvertised_thenDontScanIt()
    throws Exception
    {
        stub("/queues", page("{\"queueId\":201,\"teamType\":\"ARRANGED\"}"));
        scan();
        assertTrue(requests.contains("/divisions?" + ONE));
        assertFalse(requests.contains("/divisions?" + TWO));
    }

    @Test
    public void whenPartialUpdate_thenScanOnlyWantedLeagues()
    throws Exception
    {
        stub("/divisions?" + ONE, page(division(1, LeagueType.DIAMOND, 1),
            division(2, LeagueType.GOLD, 1)));
        stubRoster(1);
        stubRoster(2);
        scan(Map.of(QueueType.LOTV_1V1, Set.of(LeagueType.GOLD)));
        assertTrue(requests.contains("/ladders/2"));
        assertFalse(requests.contains("/ladders/1"));
        assertFalse(requests.contains("/divisions?" + TWO));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    public void whenTeamWasMoved_thenSaveOnceInNewestDivision(boolean newFirst)
    throws Exception
    {
        long newId = newFirst ? 1 : 2;
        long oldId = newFirst ? 2 : 1;
        stub("/divisions?" + ONE, page(
            division(newId, LeagueType.MASTER, 2),
            division(oldId, LeagueType.DIAMOND, 0)));
        stubRoster(newId, row(TEAM_1, "Zerg", 11));
        stubRoster(oldId, row(TEAM_1, "Zerg", 10, JOINED.minusDays(1)));
        stub("/rankings?" + ONE, page(ranking(TEAM_1, member(1, "name1#11"))));
        scan();
        assertEquals(11, ((Number) team().get("wins")).intValue());
        assertEquals(LeagueType.MASTER.getId(),
            ((Number) team().get("league_type")).intValue());
    }

    @Test
    public void whenSameJoined_thenUseLastCheckedRoster()
    throws Exception
    {
        stub("/divisions?" + ONE, page(division(1, LeagueType.MASTER, 2),
            division(2, LeagueType.DIAMOND, 0)));
        stubRoster(1, CN_SEASON, OBSERVED, OBSERVED.minusMinutes(1),
            row(TEAM_1, "Zerg", 10));
        stubRoster(2, row(TEAM_1, "Zerg", 11));
        stub("/rankings?" + ONE, page(ranking(TEAM_1, member(1, "name1#11"))));
        scan();
        assertEquals(11, ((Number) team().get("wins")).intValue());
    }

    @Test
    public void whenSameRecordAndDivision_thenKeepLastPlayed()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        stubNextRoster(row(TEAM_1, "Zerg", 10));
        scan();
        assertSameInstant(OBSERVED, odt("SELECT last_played FROM team"));
    }

    @Test
    public void whenObservedIsNull_thenLastPlayedIsNow()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        stubRoster(1, CN_SEASON, null, OBSERVED, row(TEAM_1, "Zerg", 10));
        OffsetDateTime before = SC2Pulse.offsetDateTime();
        scan();
        assertFalse(odt("SELECT last_played FROM team").isBefore(before));
        assertSameInstant(OBSERVED,
            odt("SELECT primary_data_updated FROM team"));
    }

    @Test
    public void whenDivisionHasNoTier_thenUpgradeIt()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        template.update("""
            WITH tier AS
            (
                INSERT INTO league_tier(league_id, type)
                SELECT league_id, NULL
                FROM league_tier
                INNER JOIN division ON league_tier.id = league_tier_id
                RETURNING id
            )
            UPDATE division SET league_tier_id = tier.id FROM tier
            """);
        scan();
        assertEquals(1, count("division"));
        assertEquals(LeagueTierType.SECOND.getId(), template.queryForObject
        (
            """
                SELECT league_tier.type
                FROM division
                INNER JOIN league_tier ON league_tier.id = league_tier_id
            """,
            Integer.class
        ));
    }

    @Test
    public void whenNoClansAndRaceInApi_thenKeepClansAndRaces()
    throws Exception
    {
        stubRandom("Zerg", 10);
        scan();
        PlayerCharacter character =
            playerCharacterDAO.find(REGION, 1, 1L).orElseThrow();
        Clan clan = clanDAO
            .merge(Set.of(new Clan(null, "tag", REGION, "name")))
            .iterator().next();
        clanMemberDAO.merge(Set.of(
            new ClanMember(character.getId(), clan.getId())));

        stubRoster(2, CN_SEASON, OBSERVED.plusHours(1), OBSERVED.plusHours(1),
            row(TEAM_1, "Unknown race", 11));
        scan();
        assertEquals(11, ((Number) team().get("wins")).intValue());
        assertEquals(11, template.queryForObject(
            "SELECT zerg_games_played FROM team_member", Integer.class));
        assertEquals(1,
            clanMemberDAO.find(Set.of(character.getId())).size());
    }

    @Test
    public void whenNewRandomTeamWithoutRace_thenSaveMember()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        stubRandom(null, 10);
        scan();
        assertEquals(2, count("team_member"));
    }

    @Test
    public void whenRaceIsChanged_thenReplaceGames()
    throws Exception
    {
        stubRandom("Zerg", 10);
        scan();
        stubRoster(2, CN_SEASON, OBSERVED.plusHours(1), OBSERVED.plusHours(1),
            row(TEAM_1, "Random", 11));
        scan();
        assertEquals(12, template.queryForObject(
            "SELECT random_games_played FROM team_member "
                + "WHERE zerg_games_played IS NULL", Integer.class));
    }

    @Test
    public void whenRowIsInvalid_thenSkipIt()
    throws Exception
    {
        stub1v1
        (
            row(TEAM_1, "Zerg", 10),
            row("2", null, 10),
            row("3", "Unknown race", 10),
            row("4", "Zerg", 10).replace("\"3316\"", "null"),
            row("5", "Zerg", 10).replace("\"wins\":10", "\"wins\":null"),
            row("6", "Zerg", 0).replace("\"losses\":1", "\"losses\":0"),
            row("7", "Zerg", 10),
            row("8", "Zerg", 10),
            row("9", "Zerg", 10),
            row("10", "Zerg", 10).replace("\"3316\"", "-1"),
            row("11", "Zerg", Short.MAX_VALUE)
        );
        stub("/rankings?" + ONE, page
        (
            ranking(TEAM_1, member(1, "name1#11")),
            ranking("2", member(2, "name2#11")),
            ranking("3", member(3, "name3#11")),
            ranking("4", member(4, "name4#11")),
            ranking("5", member(5, "name5#11")),
            ranking("6", member(6, "name6#11")),
            ranking("7", member(7, "name7#11"), member(8, "name8#11")),
            ranking("8"),
            ranking("9", member(9, "name9#11").replace("CN", "EU")),
            ranking("10", member(10, "name10#11")),
            ranking("11", member(11, "name11#11"))
        ));
        scan();
        assertEquals("1.1.3", team().get("legacy_id"));
    }

    @Test
    public void whenMembersAreMissing_thenLookupOnceAndRetryLater()
    throws Exception
    {
        stub1v1(row(TEAM_1, "Zerg", 10), row(TEAM_2, "Terran", 10));
        stub("/teams/" + TEAM_1 + "/members", page(member(1, "name1#11")));
        stub("/teams/" + TEAM_2 + "/members", page());
        scan();
        assertEquals("1.1.3", team().get("legacy_id"));
        //rankings are read only once per scan
        assertEquals(4, rankingRequests());

        scan();
        assertEquals(0, requests.stream()
            .filter(r->r.startsWith("/teams")).count());
        assertEquals(0, rankingRequests());
    }

    @Test
    public void whenManyMembersAreMissing_thenReadRankings()
    throws Exception
    {
        scan();
        stub1v1(row(TEAM_1, "Zerg", 10), row(TEAM_2, "Terran", 10));
        stub("/rankings?" + ONE, page(ranking(TEAM_1, member(1, "name1#11")),
            ranking(TEAM_2, member(2, "name2#11"))));
        scan();
        assertEquals(2, count("team"));
        assertEquals(1, rankingRequests());
        assertTrue(requests.stream().noneMatch(r->r.startsWith("/teams")));
    }

    @Test
    public void whenNameIsUnresolved_thenKeepStoredOrUseFakeName()
    throws Exception
    {
        stub1v1Team(10, null);
        scan();
        assertEquals("f#1", name(1));

        template.update("UPDATE player_character SET name = 'stored#1'");
        service.copyAndClearPendingData();
        stubNextRoster(row(TEAM_1, "Zerg", 11));
        scan();
        assertEquals("stored#1", name(1));
        assertEquals(1,
            service.copyAndClearPendingData().getCharacters().size());
    }

    @Test
    public void whenCachedNameIsOld_thenKeepStoredName()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        template.update("UPDATE player_character SET name = 'stored#1'");
        service.getMemberCache(REGION).put
        (
            TEAM_1,
            List.of(new SC2ArcadeLadderCharacter(
                "CN", 1, "1", "name1#11", "resolved")),
            SC2Pulse.instant().minus(SC2ArcadeLadderMemberCache.NAME_TTL)
        );
        stubNextRoster(row(TEAM_1, "Zerg", 11));
        scan();
        assertEquals("stored#1", name(1));
        assertTrue(service.getCachedCharacters(
            REGION, ((Number) team().get("season")).intValue()).isEmpty());
    }

    @Test
    public void whenDailyRefresh_thenUpdateNamesOncePerDay()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        InstantVar refreshInstant = service.getMemberRefreshInstant(REGION);
        //survives restarts
        assertEquals(refreshInstant.getValue().toEpochMilli(),
            new InstantVar(varDAO, refreshInstant.getKey())
                .getValue().toEpochMilli());

        stub1v1Team(11, "renamed#11");
        stubNextRoster(row(TEAM_1, "Zerg", 11));
        refreshInstant.setValue(
            SC2Pulse.instant().minus(Duration.ofHours(23)));
        scan();
        assertEquals(0, rankingRequests());
        assertEquals("name1#11", name(1));

        stubRoster(1, CN_SEASON, OBSERVED.plusMinutes(90),
            OBSERVED.plusMinutes(90), row(TEAM_1, "Zerg", 12));
        Instant old = SC2Pulse.instant().minus(Duration.ofHours(24));
        refreshInstant.setValue(old);
        scan();
        assertEquals(4, rankingRequests());
        assertEquals("renamed#11", name(1));
        assertTrue(refreshInstant.getValue().isAfter(old));
    }

    @Test
    public void whenRefreshFails_thenRetryOnNextScan()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        responses.put("/rankings?" + TWO,
            json("{}").setResponseCode(400));
        scan();
        //the rest of the members are not lost
        assertEquals(1, count("team"));
        assertNull(service.getMemberRefreshInstant(REGION).getValue());

        stub("/rankings?" + TWO, page());
        scan();
        assertEquals(4, rankingRequests());
        assertFalse(
            service.getMemberRefreshInstant(REGION).getValue() == null);
    }

    @Test
    public void whenCursorIsGone_thenRestartOnceAndKeepMembers()
    throws Exception
    {
        stub1v1(row(TEAM_1, "Zerg", 10));
        stub("/rankings?" + ONE, nextPage(
            ranking(TEAM_1, member(1, "name1#11"))));
        responses.put("/rankings?" + ONE + "#" + CURSOR,
            json("{}").setResponseCode(410));
        scan();
        assertEquals(1, count("team"));
        assertEquals(2, requests.stream()
            .filter(("/rankings?" + ONE)::equals).count());
    }

    @Test
    public void whenOneLadderFails_thenSaveOthers()
    throws Exception
    {
        stub("/divisions?" + ONE, page(
            division(1, LeagueType.DIAMOND, 1),
            division(2, LeagueType.DIAMOND, 1),
            division(3, LeagueType.DIAMOND, 1),
            division(4, LeagueType.DIAMOND, 1)
        ));
        stubRoster(1, row(TEAM_1, "Zerg", 10));
        responses.put("/ladders/2", json("{}").setResponseCode(503));
        stub("/ladders/3", "malformed");
        stubRoster(4, CN_SEASON - 1, OBSERVED, OBSERVED,
            row(TEAM_2, "Zerg", 10));
        stub("/rankings?" + ONE, page(ranking(TEAM_1, member(1, "name1#11")),
            ranking(TEAM_2, member(2, "name2#11"))));
        scan();
        assertEquals("1.1.3", team().get("legacy_id"));
        //bounded retries
        assertEquals(3, requests.stream()
            .filter("/ladders/2"::equals).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/coverage", "/seasons/current", "/ladders/1"})
    public void whenLoginPage_thenAbortWithoutRetry(String key)
    {
        for(int status : List.of(200, 401))
        {
            stub1v1Team(10, "name1#11");
            stubDefaults();
            stub1v1Team(10, "name1#11");
            responses.put(key, new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, "text/html")
                .setBody("<html></html>"));
            assertThrows(NoRetryException.class, this::scan);
            assertEquals(1, requests.stream().filter(key::equals).count());
            assertEquals(0, count("team"));
        }
    }

    private void stubThreshold(int min, int max)
    {
        stub("/tier-thresholds?" + ONE, page("""
            {"tier":1,"league":"DIAMOND","minRating":%s,"maxRating":%s,
            "approximate":true}
            """.formatted(min, max)));
    }

    private List<Integer> getThreshold(LeagueType league)
    {
        return template.queryForObject
        (
            """
                SELECT min_rating, max_rating
                FROM league_tier
                INNER JOIN league ON league_id = league.id
                WHERE league.type = ?
                AND league.queue_type = 201
                AND league_tier.type = 1
            """,
            (rs, i)->Arrays.asList(
                rs.getObject(1, Integer.class), rs.getObject(2, Integer.class)),
            league.getId()
        );
    }

    @Test
    public void whenTierThresholds_thenUpdateOnScanAndHourly()
    throws Exception
    {
        stubThreshold(100, 200);
        scan();
        assertEquals(List.of(100, 200), getThreshold(LeagueType.DIAMOND));

        template.update("""
            UPDATE league_tier
            SET min_rating = 1, max_rating = 2
            FROM league
            WHERE league_id = league.id AND league.type = 0
            """);
        stubThreshold(101, 201);
        scan();
        assertEquals(List.of(101, 201), getThreshold(LeagueType.DIAMOND));
        //missing thresholds keep stored ones
        assertEquals(List.of(1, 2), getThreshold(LeagueType.BRONZE));

        stubThreshold(102, 202);
        when(webServiceUtil.getCurrentOrLastOrExistingSeason(any(Region.class),
            eq(false))).thenThrow(new IllegalStateException("blizzard"));
        //other regions use Blizzard
        assertThrows(IllegalStateException.class, ()->
            statsService.updateCurrentSeasonLadderStructure(Set.of(REGION)));
        for(Region region : Region.values()) service.addRegion(region);
        //failed regions are skipped, the CN region has stubs
        for(Future<Void> task : statsService
            .updateCurrentSeasonLadderStructure(Set.of(REGION))) task.get();
        for(Region region : Region.values())
            if(region != REGION) service.removeRegion(region);
        assertEquals(List.of(102, 202), getThreshold(LeagueType.DIAMOND));
        assertEquals(List.of(1, 2), getThreshold(LeagueType.BRONZE));
    }

    private BlizzardPrivacyService createPrivacyService()
    {
        return new BlizzardPrivacyService
        (
            blizzardApi,
            statsService,
            alternativeLadderService,
            service,
            seasonDAO,
            varDAO,
            accountDAO,
            playerCharacterDAO,
            clanService,
            dbExecutorService,
            secondaryDbScheduler,
            webExecutorService,
            validator,
            webServiceUtil,
            new GlobalContext(Set.of(REGION)),
            true
        );
    }

    private void updatePrivacy()
    throws Exception
    {
        BlizzardPrivacyService privacyService = createPrivacyService();
        requests.clear();
        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();
    }

    private void expireCharacter()
    {
        template.update(
            "UPDATE player_character SET name = 'stored#1', updated = ?",
            SC2Pulse.offsetDateTime().minusDays(25));
    }

    private Map<String, Object> getPrivacyData()
    {
        return new HashMap<>(template.queryForMap("""
            SELECT player_character.name, player_character.updated,
            account.battle_tag, account.updated AS account_updated
            FROM player_character
            INNER JOIN account ON account_id = account.id
            """));
    }

    @Test
    public void whenPrivacyUpdate_thenUseCachedNamesWithoutRequests()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        expireCharacter();
        Map<String, Object> old = getPrivacyData();
        updatePrivacy();
        Map<String, Object> updated = getPrivacyData();
        assertEquals("name1#11", updated.get("name"));
        assertTrue(odt("SELECT updated FROM player_character")
            .isAfter(NOW.minusDays(1)));
        assertEquals(old.get("battle_tag"), updated.get("battle_tag"));
        assertEquals(old.get("account_updated"),
            updated.get("account_updated"));
        assertTrue(requests.isEmpty());
        verify(blizzardApi, never()).getLadders(any(), anyInt(), any(), any());
        verifyNoInteractions(clanService, webServiceUtil);
    }

    @Test
    public void whenCharactersAreAboutToExpire_thenUpdateFromArcadeInBatches()
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        expireCharacter();
        long accountId = template.queryForObject(
            "SELECT id FROM account", Long.class);
        for(long i = 2; i <= SC2ArcadeLadderAPI.CHARACTER_BATCH_SIZE + 1; i++)
            playerCharacterDAO.create(new PlayerCharacter(
                null, accountId, REGION, i, 1, "stored#1"));
        template.update("UPDATE player_character SET updated = ?",
            SC2Pulse.offsetDateTime().minusDays(25));
        //the rest is unknown to the API
        stub("/characters", page(member(1, "new#11"), member(2, null)));

        BlizzardPrivacyService privacyService = createPrivacyService();
        //force the whole backlog in a single run
        List<PlayerCharacter> batch = playerCharacterDAO.find(
            SC2Pulse.offsetDateTime(), Long.MAX_VALUE, Set.of(REGION), 1000);
        requests.clear();
        assertEquals
        (
            Set.of("new#11"),
            service.getCharacters(REGION, batch)
                .map(PlayerCharacter::getName)
                .collect(Collectors.toSet())
                .block()
        );
        assertEquals(2, requests.size());

        privacyService.getLastUpdatedCharacterId().setValue(
            playerCharacterDAO.find(REGION, 1, 1L).orElseThrow().getId() + 1);
        privacyService.getLastUpdatedCharacterInstant()
            .setValue(SC2Pulse.instant().minus(Duration.ofDays(1)));
        privacyService.update().get();
        awaitName(1, "new#11");
        assertTrue(odt("SELECT updated FROM player_character "
            + "WHERE battlenet_id = 1").isAfter(NOW.minusDays(1)));
        assertEquals("stored#1", name(2));
        verify(blizzardApi, never()).getLegacyProfiles(any(), anyBoolean());
    }

    @Test
    public void whenCharacterRequestFails_thenSkip()
    {
        responses.put("/characters", json("{}").setResponseCode(400));
        assertEquals(0L, service.getCharacters(REGION, List.of(
            new PlayerCharacter(null, null, REGION, 1L, 1, "n")))
            .count().block());
    }

    private void awaitName(long id, String name)
    throws Exception
    {
        for(int i = 0; i < 50 && !name(id).equals(name); i++) Thread.sleep(100);
        assertEquals(name, name(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "old", "season", "anonymous"})
    public void whenNoRelevantCachedNames_thenPrivacyUpdateChangesNothing
    (String type)
    throws Exception
    {
        stub1v1Team(10, "name1#11");
        scan();
        expireCharacter();
        switch(type)
        {
            case "empty" -> service.clearMemberCache();
            case "old" -> service.getMemberCache(REGION).put
            (
                TEAM_1,
                List.of(new SC2ArcadeLadderCharacter(
                    "CN", 1, "1", "name1#11", "resolved")),
                SC2Pulse.instant().minus(SC2ArcadeLadderMemberCache.NAME_TTL)
            );
            case "season" -> seasonDAO.create(new Season
            (
                null, CN_SEASON + 1, REGION, 2026, 5,
                NOW.plusDays(50), NOW.plusDays(100)
            ));
            case "anonymous" -> template.update(
                "UPDATE player_character SET anonymous = true");
            default -> throw new IllegalArgumentException(type);
        }
        Map<String, Object> old = getPrivacyData();
        updatePrivacy();
        assertEquals(old, getPrivacyData());
        assertTrue(requests.isEmpty());
    }

}
