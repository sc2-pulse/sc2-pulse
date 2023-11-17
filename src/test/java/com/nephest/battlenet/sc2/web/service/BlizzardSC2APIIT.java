// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.Patch;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BlizzardSC2APIIT
{

    public static final int RETRY_COUNT = 1;
    public static final String VALID_SEASON = "{\"seasonId\": 1, \"year\": 2010, \"number\": 1}";
    public static final String VALID_LEAGUE = "{\"type\": 0, \"queueType\": 201, \"teamType\": 0, \"tier\": []}";
    public static final String VALID_LADDER = "{\"team\": []}";
    public static final String VALID_MATCHES = "{\"matches\": []}";
    public static final String VALID_LEGACY_PROFILE = "{\"id\":\"315071\",\"realm\":1,\"displayName\":\"Serral\","
        + "\"clanName\":\"Ence\",\"clanTag\":\"ENCE\",\"profilePath\":\"/profile/2/1/315071\"}";
    public static final String VALID_PROFILE = "{\"summary\": " + VALID_LEGACY_PROFILE + "}";
    public static final String VALID_LEGACY_LADDER = "{\"ladderMembers\":[{\"character\":{\"id\":\"11445546\","
        + "\"realm\":1,\"region\":1,\"displayName\":\"Dexter\",\"clanName\":\"\",\"clanTag\":\"\","
        + "\"profilePath\":\"/profile/1/1/11445546\"}}]}";
    public static final String VALID_PROFILE_LADDER = "{\"ladderTeams\":[],\"league\":\"silver\","
        + "\"allLadderMemberships\":[{\"ladderId\":\"292783\",\"localizedGameMode\":\"1v1 Silver\"}]}";

    public static final PlayerCharacterNaturalId SERRAL =
        new PlayerCharacter(null, null, Region.EU, 315071L, 1, "Serral");
    public static final PlayerCharacterNaturalId HEROMARINE =
        new PlayerCharacter(null, null, Region.EU, 7069585L, 1, "Heromarine");
    public static final PlayerCharacterNaturalId MARU =
        new PlayerCharacter(null, null, Region.KR, 4582362L, 1, "Maru");
    public static final BlizzardPlayerCharacter BLIZZARD_CHARACTER = new BlizzardPlayerCharacter(1L, 1, "Name#123");
    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private GlobalContext globalContext;

    @Autowired
    private Validator validator;

    @Autowired
    private MockMvc mvc;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired BlizzardSC2API api, @Autowired DataSource dataSource)
    throws SQLException
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterAll
    public static void afterAll(@Autowired BlizzardSC2API api, @Autowired DataSource dataSource)
    throws SQLException
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
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
        Tuple3<Region, BlizzardPlayerCharacter[], Long> ladderId = api.getProfileLadderId(region, ladderLongId, false).block();
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
        Set<PlayerCharacterNaturalId> failedCharacters = new HashSet<>();
        api.getMatches(Set.of(SERRAL, HEROMARINE, MARU), failedCharacters, false)
            .toStream((int) (BlizzardSC2API.REQUESTS_PER_SECOND_CAP * 2))
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
    public void testFetchLegacyProfiles()
    {
        api.getLegacyProfiles(Set.of(SERRAL, HEROMARINE, MARU), false)
            .toStream((int) (BlizzardSC2API.REQUESTS_PER_SECOND_CAP * 2))
            .forEach(p->
            {
                assertNotNull(p.getT1().getName());
                assertNotNull(p.getT1().getRealm());
                assertNotNull(p.getT1().getId());
            });
    }

    @Test @Order(4)
    public void testFetchProfiles()
    {
        api.getProfiles(Set.of(SERRAL, HEROMARINE, MARU), false)
            .toStream((int) (BlizzardSC2API.REQUESTS_PER_SECOND_CAP * 2))
            .forEach(p->
            {
                assertNotNull(p.getT1().getSummary().getName());
                assertNotNull(p.getT1().getSummary().getRealm());
                assertNotNull(p.getT1().getSummary().getId());
            });
    }

    @Test @Order(5)
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
        WebServiceTestUtil.testRetrying(api.getMatches(SERRAL, false), VALID_MATCHES, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getLegacyProfile(SERRAL, false), VALID_LEGACY_PROFILE, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getProfile(SERRAL, false), VALID_PROFILE, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getProfileLadderId(Region.US, 292783, false), VALID_LEGACY_LADDER, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getProfileLadderMono(Region.US, BLIZZARD_CHARACTER, 292783L, queueTypes, false),
            VALID_PROFILE_LADDER, server, RETRY_COUNT);
        server.shutdown();

        api.setRegionUri(null);
        api.setWebClient(oldWebClient);
    }


    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testForceRegion()
    throws Exception
    {
        mvc.perform
        (
            post("/admin/blizzard/api/region/US/force/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        assertEquals(Region.EU, api.getRegion(Region.US));
        assertEquals(Region.KR, api.getRegion(Region.KR));

        mvc.perform
        (
            delete("/admin/blizzard/api/region/US/force")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
        .andExpect(status().isOk())
        .andReturn();

        assertEquals(Region.US, api.getRegion(Region.US));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testIgnoreSllErrors()
    throws Exception
    {
        assertEquals(false, api.isIgnoreClientSslErrors(Region.EU));
        assertEquals(false, api.isIgnoreClientSslErrors(Region.US));
        mvc.perform
        (
            post("/admin/blizzard/api/ssl/error/ignore/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(true, api.isIgnoreClientSslErrors(Region.EU));
        assertEquals(false, api.isIgnoreClientSslErrors(Region.US));

        mvc.perform
        (
            delete("/admin/blizzard/api/ssl/error/ignore/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(false, api.isIgnoreClientSslErrors(Region.EU));
        assertEquals(false, api.isIgnoreClientSslErrors(Region.US));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSetTimeout()
    throws Exception
    {
        assertEquals(BlizzardSC2API.IO_TIMEOUT, api.getTimeout(Region.EU));
        assertEquals(BlizzardSC2API.IO_TIMEOUT, api.getTimeout(Region.US));
        mvc.perform
        (
            post("/admin/blizzard/api/timeout/EU/1000")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(Duration.ofMillis(1000), api.getTimeout(Region.EU));
        assertEquals(BlizzardSC2API.IO_TIMEOUT, api.getTimeout(Region.US));

        mvc.perform
        (
            delete("/admin/blizzard/api/timeout/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(BlizzardSC2API.IO_TIMEOUT, api.getTimeout(Region.EU));
        assertEquals(BlizzardSC2API.IO_TIMEOUT, api.getTimeout(Region.US));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSetRequestsPerSecondCap()
    throws Exception
    {
        Region targetRegion = globalContext.getActiveRegions().iterator().next();
        for(Region region : globalContext.getActiveRegions())
            assertEquals
            (
                BlizzardSC2API.REQUESTS_PER_SECOND_CAP,
                api.getRequestsPerSecondCap(region)
            );

        mvc.perform
        (
            post("/admin/blizzard/api/rps/{region}/5", targetRegion.name())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : globalContext.getActiveRegions()) assertEquals
        (
            api.isSeparateRequestLimits()
                ? region == targetRegion ? 5 : BlizzardSC2API.REQUESTS_PER_SECOND_CAP
                : 5,
            api.getRequestsPerSecondCap(region)
        );

        mvc.perform
        (
            delete("/admin/blizzard/api/rps/{region}", targetRegion.name())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : globalContext.getActiveRegions())
            assertEquals
            (
                BlizzardSC2API.REQUESTS_PER_SECOND_CAP,
                api.getRequestsPerSecondCap(region)
            );
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSetRequestsPerHourCap()
    throws Exception
    {
        Region targetRegion = globalContext.getActiveRegions().iterator().next();
        for(Region region : globalContext.getActiveRegions())
            assertEquals
            (
                BlizzardSC2API.REQUESTS_PER_HOUR_CAP,
                api.getRequestsPerHourCap(region)
            );

        mvc.perform
        (
            post("/admin/blizzard/api/rph/{region}/1000", targetRegion.name())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : globalContext.getActiveRegions()) assertEquals
        (
            api.isSeparateRequestLimits()
                ? region == targetRegion ? 1000 : BlizzardSC2API.REQUESTS_PER_HOUR_CAP
                : 1000,
            api.getRequestsPerHourCap(region)
        );

        mvc.perform
        (
            delete("/admin/blizzard/api/rph/{region}", targetRegion.name())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : globalContext.getActiveRegions())
            assertEquals
            (
                BlizzardSC2API.REQUESTS_PER_HOUR_CAP,
                api.getRequestsPerHourCap(region)
            );
    }

    @Test
    public void testAutoForceRegion()
    {
        api.setAutoForceRegion(true);

        assertEquals(Region.US, api.getRegion(Region.US)); //no redirect

        APIHealthMonitor monitor = api.getHealthMonitor(Region.US, false);
        monitor.update();
        monitor.addRequest();
        monitor.addError();
        monitor.update(); //error rate is 100
        api.autoForceRegion();

        assertEquals(Region.KR, api.getRegion(Region.US)); //redirect to KR

        monitor.update(); //error rate is 0
        api.autoForceRegion();

        assertEquals(Region.KR, api.getRegion(Region.US)); //redirect to KR

        //the force region duration has ended
        api.setForceRegionInstant(Region.US, Instant.now().minusSeconds(BlizzardSC2API.AUTO_FORCE_REGION_MAX_DURATION.toSeconds() + 1));
        api.autoForceRegion();

        //no redirect
        assertEquals(Region.US, api.getRegion(Region.US));

        api.setAutoForceRegion(false);
    }

    @Test
    public void testFetchPlayerCharacters()
    {
        List<BlizzardFullPlayerCharacter> chars = api
            .getPlayerCharacters(Region.EU, 123595611L)
            .collectList()
            .block();
        assertEquals(3, chars.size());
        chars.sort(Comparator.comparing(BlizzardFullPlayerCharacter::getRegion));
        verifyPlayerCharacter(chars.get(0), Region.US, 1, 5109270);
        verifyPlayerCharacter(chars.get(1), Region.EU, 1, 2895287);
        verifyPlayerCharacter(chars.get(2), Region.KR, 1, 6491133);
    }

    private static void verifyPlayerCharacter
    (
        BlizzardFullPlayerCharacter character,
        Region expectedRegion,
        int expectedRealm,
        long expectedId
    )
    {
        assertEquals(expectedRegion, character.getRegion());
        assertEquals(expectedRealm, character.getRealm());
        assertEquals(expectedId, character.getId());
        assertNotNull(character.getName());
    }

    @Test
    public void testFetchPatches()
    {
        List<Patch> patches = api
            .getPatches(Region.US, 0L, 2)
            .collectList()
            .block();
        assertEquals(2, patches.size());
        for(Patch patch : patches)
        {
            assertNotNull(patch.getId());
            assertNotNull(patch.getVersion());
            assertNotNull(patch.getPublished());
        }
    }


}
