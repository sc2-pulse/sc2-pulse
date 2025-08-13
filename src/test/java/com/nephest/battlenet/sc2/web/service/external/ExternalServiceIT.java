// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.arcade.ArcadePlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ExternalServiceIT
{

    private static final Logger LOG = LoggerFactory.getLogger(ExternalServiceIT.class);

    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE =
        BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private PlayerCharacterLinkDAO playerCharacterLinkDAO;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SC2ArcadeAPI arcadeAPI;

    @Autowired
    private Environment environment;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    private MockMvc mvc;
    private PlayerCharacter character, character2;
    private ExternalLinkResolveResult[] expectedResult;


    private static Set<SocialMedia> ENABLED_TYPES;

    @BeforeAll
    public static void beforeAll(@Autowired List<ExternalCharacterLinkResolver> resolvers)
    {
        ENABLED_TYPES = resolvers.stream()
            .map(ExternalCharacterLinkResolver::getSupportedSocialMedia)
            .collect(Collectors.toSet());
    }

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
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
        init();
    }

    private void init()
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 2, region, 2020, 2,
            SC2Pulse.offsetDateTime(2020, 2, 1), SC2Pulse.offsetDateTime(2020, 3, 1));
        seasonGenerator.generateSeason
        (
            List.of(season1),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TEAM_TYPE,
            TIER_TYPE,
            3
        );
        Division bronze1v1 = divisionDAO.findListByLadder
        (
            season1.getBattlenetId(),
            region,
            BaseLeague.LeagueType.BRONZE,
            QueueType.LOTV_1V1,
            TEAM_TYPE,
            TIER_TYPE
        ).get(0);

        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        character = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), region, 315071L, 1, "name#1"));
        character2 = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), region, 3141896L, 1, "name#2"));
        seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE),
            TIER_TYPE, bronze1v1,
            TeamLegacyId.trusted("10002"), 1L, 1, 2, 3, 4,
            character
        );
        seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE),
            TIER_TYPE, bronze1v1,
            TeamLegacyId.trusted("10003"), 1L, 1, 2, 3, 4,
            character2
        );
        playerCharacterStatsDAO.mergeCalculate();
        expectedResult = new ExternalLinkResolveResult[]
        {
            new ExternalLinkResolveResult
            (
                character.getId(),
                Stream.of
                (
                    new PlayerCharacterLink
                    (
                        character.getId(),
                        SocialMedia.BATTLE_NET,
                        "2/4771787010354446336"
                    ),
                    new PlayerCharacterLink
                    (
                        character.getId(),
                        SocialMedia.REPLAY_STATS,
                        "125470"
                    )
                )
                    .filter(link->ENABLED_TYPES.contains(link.getType()))
                    .toList(),
                Set.of()
            ),
            new ExternalLinkResolveResult
            (
                character2.getId(),
                Stream.of
                (
                    new PlayerCharacterLink
                    (
                        character2.getId(),
                        SocialMedia.BATTLE_NET,
                        "2/6699777368304648192"
                    ),
                    new PlayerCharacterLink
                    (
                        character2.getId(),
                        SocialMedia.REPLAY_STATS,
                        "22042"
                    )
                )
                    .filter(link->ENABLED_TYPES.contains(link.getType()))
                    .toList(),
                Set.of()
            )
        };
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
    public void testExternalCharacterSearchByBattleNetProfile()
    throws Exception
    {
        verifyExternalCharacterSearchByBattleNetProfile();
        verifyExternalCharacterSearchByBattleNetProfile();
        verifyExternalLinkResolver();
        /*
            The API was called once despite several searches was run. Previous search results
            should be persisted in the DB and used when possible to avoid redundant API calls.
         */
        verify(arcadeAPI, times(1)).findByRegionAndGameId(any(), anyLong());
    }

    @Test
    public void testExternalLinkResolver() throws Exception
    {
        verifyExternalLinkResolver();
        verifyExternalLinkResolver();
        verifyExternalCharacterSearchByBattleNetProfile();

        /*
            The API was called twice despite several searches was run. Previous search results
            should be persisted in the DB and used when possible to avoid redundant API calls.
         */
        verifyDbLinks();
        verify(arcadeAPI, times(2)).findCharacter(any());
    }

    private ExternalLinkResolveResult[] getLinks(ResultMatcher resultMatcher)
    throws Exception
    {
        ExternalLinkResolveResult[] result = objectMapper.readValue(mvc.perform
        (
            get("/api/character-links")
                .queryParam
                (
                    "characterId",
                    String.valueOf(character.getId()),
                    String.valueOf(character2.getId())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(resultMatcher)
            .andReturn().getResponse().getContentAsString(), ExternalLinkResolveResult[].class);
        Arrays.sort(result, Comparator.comparing(ExternalLinkResolveResult::playerCharacterId));
        for(ExternalLinkResolveResult curResult : result)
            curResult.links().sort(PlayerCharacterLink.NATURAL_ID_COMPARATOR);
        return result;
    }

    @Test
    public void whenExternalLinksResolverFails_thenAddFailedType()
    throws Exception
    {
        Exception ex = WebClientResponseException.InternalServerError.create(500, "ISE", null, null, null);
        doReturn(Mono.error(new RuntimeException(ex)))
            .when(arcadeAPI).findCharacter(character);
        Assertions.assertThat(getLinks(status().is5xxServerError()))
            .usingRecursiveComparison()
            .isEqualTo(new ExternalLinkResolveResult[] {
                new ExternalLinkResolveResult
                (
                    character.getId(),
                    expectedResult[0].links().stream()
                        .filter(link->link.getType() != SocialMedia.BATTLE_NET)
                        .toList(),
                    Set.of(SocialMedia.BATTLE_NET)
                ),
                expectedResult[1]
            });
    }

    private void verifyMissingLink()
    throws Exception
    {
        Assertions.assertThat(getLinks(status().isOk()))
            .usingRecursiveComparison()
            .isEqualTo(new ExternalLinkResolveResult[] {
                new ExternalLinkResolveResult
                (
                    character.getId(),
                    expectedResult[0].links().stream()
                        .filter(link->link.getType() != SocialMedia.BATTLE_NET)
                        .toList(),
                    Set.of()
                ),
                expectedResult[1]
            });
    }

    @Test
    public void whenExternalLinkResolverReturnsEmptyResult_thenSkipIt()
    throws Exception
    {
        //char without a battlenet::// link
        ArcadePlayerCharacter arcadeChar = new ArcadePlayerCharacter(1L, 2, "name#1", Region.EU, null);
        doReturn(Mono.just(arcadeChar)).when(arcadeAPI).findCharacter(character);
        verifyMissingLink();
    }

    @Test
    public void whenExternalLinksResolverThrowsNotFoundException_thenSkipIt()
    throws Exception
    {
        Exception ex = WebClientResponseException.NotFound.create(404, "ISE", null, null, null);
        doReturn(Mono.error(new RuntimeException(ex))).when(arcadeAPI).findCharacter(character);
        verifyMissingLink();
    }

    public static Stream<Arguments> testTypeFilter()
    {
        return ENABLED_TYPES.stream().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    public void testTypeFilter(SocialMedia type)
    throws Exception
    {
        ExternalLinkResolveResult[] result = objectMapper.readValue(mvc.perform
        (
            get("/api/character-links")
                .queryParam
                (
                    "characterId",
                    String.valueOf(character.getId()),
                    String.valueOf(character2.getId())
                )
                .queryParam
                (
                    "type",
                    mvcConversionService.convert(type, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ExternalLinkResolveResult[].class);
        Arrays.sort(result, Comparator.comparing(ExternalLinkResolveResult::playerCharacterId));
        for(ExternalLinkResolveResult curResult : result)
            curResult.links().sort(PlayerCharacterLink.NATURAL_ID_COMPARATOR);
        Assertions.assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(new ExternalLinkResolveResult[] {
                new ExternalLinkResolveResult
                (
                    character.getId(),
                    expectedResult[0].links().stream()
                        .filter(link->link.getType() == type)
                        .toList(),
                    Set.of()
                ),
                new ExternalLinkResolveResult
                (
                    character2.getId(),
                    expectedResult[1].links().stream()
                        .filter(link->link.getType() == type)
                        .toList(),
                    Set.of()
                )
            });
    }

    private void verifyExternalCharacterSearchByBattleNetProfile()
    throws Exception
    {
        String[] prefixes = new String[]
        {
            "battlenet:://starcraft/",
            "battlenet://starcraft/",
            "battlenet://",
            "starcraft:://",
            "starcraft://",
            "starcraft-whatever://"
        };
        for(String prefix : prefixes)
        {
            LOG.info("Testing {} prefix", prefix);
            verifyExternalCharacterSearchByBattleNetProfile(prefix);
        }
    }

    private void verifyExternalCharacterSearchByBattleNetProfile(String prefix)
    throws Exception
    {
        LadderDistinctCharacter[] characterFound = objectMapper.readValue(mvc.perform
        (
            get("/api/characters")
                .queryParam("query", prefix + "profile/2/4771787010354446336")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderDistinctCharacter[].class);
        assertEquals(1, characterFound.length);
        assertEquals(character, characterFound[0].getMembers().getCharacter());
    }

    private void verifyExternalLinkResolver()
    throws Exception
    {
        Assertions.assertThat(getLinks(status().isOk()))
            .usingRecursiveComparison()
            .isEqualTo(expectedResult);
    }

    private void verifyDbLinks()
    {
        Assertions.assertThat
        (
            playerCharacterLinkDAO.find(Set.of(character.getId(), character2.getId()), Set.of())
        )
                .usingRecursiveComparison()
                .isEqualTo
                (
                    Arrays.stream(expectedResult)
                        .map(ExternalLinkResolveResult::links)
                        .flatMap(Collection::stream)
                        .toList()
                );
    }

}
