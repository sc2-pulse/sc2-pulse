// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BlizzardSC2APIIT
{

    public static final int RETRY_COUNT = 2;
    public static final String VALID_SEASON = "{\"seasonId\": 1, \"year\": 2010, \"number\": 1}";
    public static final String VALID_LEAGUE = "{\"type\": 0, \"queueType\": 201, \"teamType\": 0, \"tier\": []}";
    public static final String VALID_LADDER = "{\"team\": []}";
    public static final String VALID_MATCHES = "{\"matches\": []}";
    public static final String VALID_LEGACY_LADDER = "{\"ladderMembers\":[{\"character\":{\"id\":\"11445546\","
        + "\"realm\":1,\"region\":1,\"displayName\":\"Dexter\",\"clanName\":\"\",\"clanTag\":\"\","
        + "\"profilePath\":\"/profile/1/1/11445546\"}}]}";
    public static final String VALID_PROFILE_LADDER = "{\"ladderTeams\":[],\"league\":\"silver\","
        + "\"allLadderMemberships\":[{\"ladderId\":\"292783\",\"localizedGameMode\":\"1v1 Silver\"}]}";

    public static final PlayerCharacter SERRAL =
        new PlayerCharacter(null, null, Region.EU, 315071L, 1, "Serral");
    public static final PlayerCharacter HEROMARINE =
        new PlayerCharacter(null, null, Region.EU, 7069585L, 1, "Heromarine");
    public static final PlayerCharacter MARU =
        new PlayerCharacter(null, null, Region.KR, 4582362L, 1, "Maru");
    public static final BlizzardPlayerCharacter BLIZZARD_CHARACTER = new BlizzardPlayerCharacter(1L, 1, "Name#123");
    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private Validator validator;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired BlizzardSC2API api)
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
    }

    @AfterAll
    public static void afterAll(@Autowired BlizzardSC2API api)
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
    }

    @Test @Order(1) @Disabled("Blizzard API fails too often now, ignore this test until it becomes more stable")
    public void testFetch()
    {
        BlizzardSeason season = api.getCurrentSeason(Region.EU).block();
        assertNotNull(season);
        assertNotNull(season.getId());
        assertNotNull(season.getNumber());
        assertNotNull(season.getYear());
        assertNotNull(season.getStart());
        assertNotNull(season.getEnd());
        BlizzardLeague league =
            api.getLeague(Region.EU, season, BaseLeague.LeagueType.MASTER, QueueType.LOTV_1V1, TeamType.ARRANGED).block();
        assertNotNull(league);
        assertEquals(BaseLeague.LeagueType.MASTER, league.getType());
        assertEquals(QueueType.LOTV_1V1, league.getQueueType());
        assertEquals(TeamType.ARRANGED, league.getTeamType());
        assertTrue(league.getTiers().length > 0);

        BlizzardSeason historicalSeason = api.getSeason(Region.EU, 40).block();
        Errors errors = new BeanPropertyBindingResult(historicalSeason, historicalSeason.toString());
        validator.validate(historicalSeason, errors);
        assertFalse(errors.hasErrors());

        //45 was the last season when this test was written
        assertTrue(api.getLastSeason(Region.EU, 45).block().getId() > 44);
        assertTrue(api.getLastSeason(Region.EU, 44).block().getId() > 44);
        assertThrows(IllegalStateException.class, ()->api.getLastSeason(Region.EU, 999999).block());
        BlizzardSeason currentSeason = api.getCurrentOrLastSeason(Region.EU, 45).block();
        assertTrue(currentSeason.getId() > 44);

        testFetchAlternative();
    }

    private void testFetchAlternative()
    {
        api.getProfileLadder(Tuples.of(Region.EU, new BlizzardPlayerCharacter[]{
                new BlizzardPlayerCharacter(1L, 1, "Name#123"),
                new BlizzardPlayerCharacter(2L, 2, "Name2#123"),
                new BlizzardPlayerCharacter(3L, 3, "Name#123")},
            1L), Set.of(QueueType.LOTV_1V1))
            .onErrorResume(t->Mono.empty())
            .block();
        testFetchAlternative(Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED);
        testFetchAlternative(Region.US, QueueType.LOTV_4V4, TeamType.RANDOM);
        testFetchAlternative(Region.US, QueueType.LOTV_ARCHON, TeamType.ARRANGED);
    }
    
    private void testFetchAlternative(Region region, QueueType queueType, TeamType teamType)
    {
        BaseLeague.LeagueType leagueType = BaseLeague.LeagueType.DIAMOND;
        Set<QueueType> queueTypes = Set.of(queueType);
        BlizzardSeason bSeason = api.getCurrentOrLastSeason(region, 46).block();
        long ladderLongId = api.getLeague(region, bSeason, leagueType, queueType,teamType, true)
            .block()
            .getTiers()[0].getDivisions()[0].getLadderId();
        Tuple3<Region, BlizzardPlayerCharacter[], Long> ladderId = api.getProfileLadderId(region, ladderLongId).block();
        assertEquals(region, ladderId.getT1());
        assertEquals(ladderLongId, ladderId.getT3());
        assertNotNull(ladderId.getT2());
        Arrays.stream(ladderId.getT2()).forEach(Assertions::assertNotNull);

        Errors errors = new BeanPropertyBindingResult(ladderId.getT2(), ladderId.getT2().toString());
        validator.validate(ladderId.getT2(), errors);
        assertFalse(errors.hasErrors());

        BlizzardProfileLadder ladder = api
            .getProfileLadder(Tuples.of(ladderId.getT1(), ladderId.getT2(), ladderId.getT3()), queueTypes).block();
        BlizzardProfileTeam[] teams = ladder.getLadderTeams();
        assertTrue(teams.length > 0);
        Errors teamErrors = new BeanPropertyBindingResult(teams, teams.toString());
        validator.validate(teams, teamErrors);
        assertFalse(teamErrors.hasErrors());
        assertEquals(queueType.getTeamFormat().getMemberCount(teamType), teams[0].getTeamMembers().length);
        assertEquals(leagueType, ladder.getLeague().getType());
        assertEquals(queueType, ladder.getLeague().getQueueType());
        assertEquals(teamType, ladder.getLeague().getTeamType());
    }

    @Test @Order(2)
    public void testFetchMatches()
    {
        Set<PlayerCharacter> failedCharacters = new HashSet<>();
        api.getMatches(Set.of(SERRAL, HEROMARINE, MARU), failedCharacters)
            .sequential()
            .toStream(BlizzardSC2API.SAFE_REQUESTS_PER_SECOND_CAP * 2)
            .forEach((m)->
            {
                assertTrue(m.getT1().getMatches().length > 0);
                for(BlizzardMatch match : m.getT1().getMatches())
                {
                    Errors errors = new BeanPropertyBindingResult(match, match.toString());
                    validator.validate(match, errors);
                    assertFalse(errors.hasErrors());
                }
            });
        assertTrue(failedCharacters.isEmpty());
    }

    @Test @Order(3)
    public void testRetrying()
    throws Exception
    {
        Set<QueueType> queueTypes = Set.of(QueueType.LOTV_1V1);
        MockWebServer server = new MockWebServer();
        server.start();
        api.setRegionUri(server.url("/someurl").uri().toString());
        WebClient oldWebClient = api.getWebClient();
        api.setWebClient(WebServiceTestUtil.createTimeoutClient());

        WebServiceTestUtil.testRetrying(api.getCurrentSeason(Region.EU), VALID_SEASON, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying
        (
            api.getLeague
            (
                Region.EU,
                mock(BlizzardSeason.class),
                BlizzardLeague.LeagueType.BRONZE,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            VALID_LEAGUE, server, RETRY_COUNT
        );
        WebServiceTestUtil.testRetrying(api.getLadder(Region.EU, mock(BlizzardTierDivision.class)), VALID_LADDER, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getLadder(Region.EU, 1L), VALID_LADDER, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getMatches(SERRAL), VALID_MATCHES, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getProfileLadderId(Region.US, 292783), VALID_LEGACY_LADDER, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getProfileLadderMono(Region.US, BLIZZARD_CHARACTER, 292783L, queueTypes),
            VALID_PROFILE_LADDER, server, RETRY_COUNT);
        server.shutdown();

        api.setRegionUri(null);
        api.setWebClient(oldWebClient);
    }

}
