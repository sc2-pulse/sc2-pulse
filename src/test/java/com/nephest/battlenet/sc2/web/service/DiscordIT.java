// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.discord.SpringDiscordClient;
import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.discord.DiscordIdentity;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.DiscordUserMeta;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonPersonalData;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.web.util.MonoUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class DiscordIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private AccountDiscordUserDAO accountDiscordUserDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private DiscordService discordService;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    public static final String BATTLE_TAG = "battletag#0";

    private static DiscordAPI originalAPI;

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

        DiscordAPI mockAPI = mock(DiscordAPI.class);
        discordService.setDiscordAPI(mockAPI);
    }

    @BeforeAll
    public static void beforeAll(@Autowired DiscordService discordService)
    {
        originalAPI = discordService.getDiscordAPI();
    }

    @AfterAll
    public static void afterAll
    (
        @Autowired DataSource dataSource,
        @Autowired DiscordService discordService
    )
    throws SQLException
    {
        discordService.setDiscordAPI(originalAPI);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = BATTLE_TAG)
    public void testChain()
    throws Exception
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        playerCharacterStatsDAO.mergeCalculate();
        DiscordUser discordUser = new DiscordUser(Snowflake.of(123L), "name", 123);

        discordService.linkAccountToNewDiscordUser(2L, discordUser);
        verifyLinkedDiscordUser(1L, null);
        //invisible by default
        verifyLinkedDiscordUser(2L, discordUser, false);

        //visible
        discordService.setVisibility(2L, true);
        verifyLinkedDiscordUser(2L, discordUser, true);

        //invisible
        discordService.setVisibility(2L, false);
        verifyLinkedDiscordUser(2L, discordUser, false);

        //back to visible for further test chain
        discordService.setVisibility(2L, true);
        verifyLinkedDiscordUser(2L, discordUser, true);

        //previous link is removed, one-to-one relationship
        discordService.linkAccountToDiscordUser(1L, discordUser.getId());
        discordService.setVisibility(1L, true);
        verifyLinkedDiscordUser(1L, discordUser);
        verifyLinkedDiscordUser(2L, null);

        //simulate oauth authorization, should be removed when unlinking the discord account
        OAuth2AuthorizedClient client = WebServiceTestUtil.createOAuth2AuthorizedClient
        (
            clientRegistrationRepository.findByRegistrationId(DiscordAPI.USER_CLIENT_REGISTRATION_ID),
            "1"
        );
        oAuth2AuthorizedClientService.saveAuthorizedClient(client, authentication);
        assertNotNull(oAuth2AuthorizedClientService.loadAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1"));

        //second links is removed, no linked chars
        Tuple2<Mono<Void>, Mono<Void>> mono = MonoUtil.verifiableMono();
        doReturn(mono.getT1())
            .when(discordService.getDiscordAPI()).updateConnectionMetaData(any(), any());
        Tuple2<Mono<Void>, Mono<Void>> mono2 = MonoUtil.verifiableMono();
        doReturn(mono2.getT1().flux())
            .when(discordService.getDiscordAPI()).revokeRefreshToken(any());
        stubNoManagedGuilds();
        when(stubGatewayClient(discordService.getDiscordAPI()).getGuilds()).thenReturn(Flux.empty());
        when(discordService.getDiscordAPI().getAuthorizedClient(1L))
            .thenReturn(Optional.of(client));
        mvc.perform
        (
            post("/api/my/discord/unlink")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk());
        verifyLinkedDiscordUser(1L, null);
        verifyLinkedDiscordUser(2L, null);
        assertNull(oAuth2AuthorizedClientService.loadAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1"));

        //roles are dropped
        ArgumentCaptor<ApplicationRoleConnection> connectionArgumentCaptor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);
        verify(discordService.getDiscordAPI())
            .updateConnectionMetaData(eq(client), connectionArgumentCaptor.capture());
        ApplicationRoleConnection connection = connectionArgumentCaptor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(BATTLE_TAG, connection.getPlatformUsername());
        assertNull(connection.getMetadata());
        mono.getT2().block();
        mono2.getT2().block();
    }

    private void verifyLinkedDiscordUser(Long characterId, DiscordUser discordUser)
    throws Exception
    {
        CommonCharacter commonChar = WebServiceTestUtil.getObject
        (
            mvc, objectMapper, CommonCharacter.class,
            "/api/character/{id}/common", characterId
        );
        if(discordUser == null)
        {
            assertNull(commonChar.getDiscordUser());
        }
        else
        {
            assertDeepEquals(discordUser, commonChar.getDiscordUser());
        }
    }

    private void verifyLinkedDiscordUser
    (
        long characterId,
        DiscordUser discordUser,
        boolean isVisible
    )
    throws Exception
    {
        verifyLinkedDiscordUser(characterId, isVisible ? discordUser : null);
        DiscordUserMeta meta = accountDiscordUserDAO.findMeta(discordUser.getId()).orElseThrow();
        assertEquals(isVisible, meta.isPublic());
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = BATTLE_TAG)
    public void testPersonalData()
    throws Exception
    {
        accountDAO.merge(new Account(null, Partition.GLOBAL, BATTLE_TAG));
        playerCharacterStatsDAO.mergeCalculate();

        DiscordUser discordUser = new DiscordUser(Snowflake.of(123L), "name", 123);
        discordService.linkAccountToNewDiscordUser(1L, discordUser);

        CommonPersonalData data = WebServiceTestUtil
            .getObject(mvc, objectMapper, CommonPersonalData.class, "/api/my/common");
        assertNotNull(data.getDiscordUser());
        assertDeepEquals(discordUser, data.getDiscordUser().getUser());
        //invisible by default
        assertEquals(false, data.getDiscordUser().getMeta().isPublic());

        //make connection visible
        mvc.perform
        (
            post("/api/my/discord/public/true")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk());

        CommonPersonalData data2 = WebServiceTestUtil
            .getObject(mvc, objectMapper, CommonPersonalData.class, "/api/my/common");
        assertEquals(true, data2.getDiscordUser().getMeta().isPublic());
    }

    @Test
    public void testUpdateDiscordUser()
    {
        DiscordUser discordUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(123L), "name123", 123)))
            .iterator().next();
        DiscordUser foundDiscordUser = discordUserDAO.find(Set.of(Snowflake.of(123L))).get(0);
        assertEquals(discordUser.getId(), foundDiscordUser.getId());
        assertEquals(discordUser.getName(), foundDiscordUser.getName());
        assertEquals(discordUser.getDiscriminator(), foundDiscordUser.getDiscriminator());

        discordUserDAO.merge(Set.of(
            new DiscordUser(Snowflake.of(123L), "name321", 321),
            //new
            new DiscordUser(Snowflake.of(456L), "name456", 456),
            new DiscordUser(Snowflake.of(567L), "name567", 567)
        ));

        DiscordUser updatedUser = discordUserDAO.find(Set.of(Snowflake.of(123L))).get(0);
        assertEquals(Snowflake.of(123L), updatedUser.getId());
        assertEquals("name321", updatedUser.getName());
        assertEquals(321, updatedUser.getDiscriminator());

        verifyStdDiscordUser(discordUserDAO.find(Set.of(Snowflake.of(567L))).get(0), 567);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNullDiscriminator()
    {
        User zeroUser = mock(User.class);
        when(zeroUser.getId()).thenReturn(Snowflake.of(123L));
        when(zeroUser.getUsername()).thenReturn("name123");
        when(zeroUser.getDiscriminator()).thenReturn("0");

        User legacyUser = mock(User.class);
        when(legacyUser.getId()).thenReturn(Snowflake.of(123L));
        when(legacyUser.getUsername()).thenReturn("name123");
        when(legacyUser.getDiscriminator()).thenReturn("123");

        //123
        DiscordUser user = discordUserDAO.merge(Set.of(DiscordUser.from(legacyUser)))
            .iterator().next();
        DiscordUser foundDiscordUser = discordUserDAO.find(Set.of(user.getId())).get(0);
        assertEquals(123, foundDiscordUser.getDiscriminator());

        //from 123 to null
        discordUserDAO.merge(Set.of(DiscordUser.from(zeroUser)));
        foundDiscordUser = discordUserDAO.find(Set.of(user.getId())).get(0);
        assertNull(foundDiscordUser.getDiscriminator());

        //from null to 123
        discordUserDAO.merge(Set.of(DiscordUser.from(legacyUser)));
        foundDiscordUser = discordUserDAO.find(Set.of(user.getId())).get(0);
        assertEquals(123, foundDiscordUser.getDiscriminator());

        //null
        DiscordUser nullUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(321L), "name123", null)))
            .iterator().next();
        foundDiscordUser = discordUserDAO.find(Set.of(nullUser.getId())).get(0);
        assertNull(foundDiscordUser.getDiscriminator());
    }

    @Test
    public void whenEmptyDiscriminatorIsBeforeExistingDiscriminator_thenThereShouldBeNoException()
    {
        discordUserDAO.merge(Set.of(
            new DiscordUser(Snowflake.of(1L), "name1", null),
            new DiscordUser(Snowflake.of(2L), "name2", 1234)
        ));
    }

    @Test
    public void testRemoveEmptyDiscordUsers()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));

        DiscordUser[] users = discordUserDAO.merge(new LinkedHashSet<>(List.of(
            new DiscordUser(Snowflake.of(1L), "name1", 1),
            new DiscordUser(Snowflake.of(2L), "name2", 2),
            new DiscordUser(Snowflake.of(3L), "name3", 3),
            new DiscordUser(Snowflake.of(4L), "name4", 4)
        )))
            .toArray(DiscordUser[]::new);

        discordService.linkAccountToDiscordUser(acc1.getId(), users[0].getId());
        discordService.linkAccountToDiscordUser(acc3.getId(), users[2].getId());

        assertEquals(2, discordUserDAO.removeUsersWithNoAccountLinked());
        List<DiscordUser> foundUsers = discordUserDAO.findByIdCursor(Snowflake.of(0L), 10);
        foundUsers.sort(Comparator.comparing(DiscordUser::getId));
        assertEquals(2, foundUsers.size());
        verifyStdDiscordUser(foundUsers.get(0), 1);
        verifyStdDiscordUser(foundUsers.get(1), 3);
    }

    @Test
    public void testFindByDiscordUserId()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));

        DiscordUser[] users = discordUserDAO.merge(new LinkedHashSet<>(List.of(
            new DiscordUser(Snowflake.of(1L), "name1", 1),
            new DiscordUser(Snowflake.of(2L), "name2", 2)
        )))
            .toArray(DiscordUser[]::new);

        discordService.linkAccountToDiscordUser(acc1.getId(), users[0].getId());

        assertEquals(acc1, accountDAO.findByDiscordUserId(1L).get());
        assertTrue(accountDAO.findByDiscordUserId(2L).isEmpty());
    }

    @Test
    public void testFindMainTeam()
    {
        Tuple3<Account, PlayerCharacter[], Team> main = stubMainTeam();
        assertEquals(main.getT3(), discordService.findMainTeam(main.getT1().getId()).get());
    }

    @Test
    public void whenLadderCharacterActivityEventReceived_thenUpdateRoles()
    {
        Tuple3<Account, PlayerCharacter[], Team> main = stubMainTeam();
        Tuple2<Mono<Void>, Mono<Void>> mono = MonoUtil.verifiableMono();
        doReturn(mono.getT1())
            .when(discordService.getDiscordAPI()).updateConnectionMetaData(any(), any());
        OAuth2AuthorizedClient client = WebServiceTestUtil.createOAuth2AuthorizedClient
        (
            clientRegistrationRepository.findByRegistrationId(DiscordAPI.USER_CLIENT_REGISTRATION_ID),
            String.valueOf(main.getT1().getId())
        );
        when(discordService.getDiscordAPI().getAuthorizedClient(main.getT1().getId()))
            .thenReturn(Optional.of(client));
        DiscordUser discordUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(1L), "name", 1)))
            .iterator().next();
        accountDiscordUserDAO.create(Set.of(new AccountDiscordUser(main.getT1().getId(), discordUser.getId())));
        stubNoManagedGuilds();
        eventService.createLadderCharacterActivityEvent(main.getT2()[0]);
        mono.getT2().block();

        ArgumentCaptor<ApplicationRoleConnection> captor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);
        verify(discordService.getDiscordAPI())
            .updateConnectionMetaData(eq(client), captor.capture());
        ApplicationRoleConnection connection = captor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(main.getT1().getBattleTag(), connection.getPlatformUsername());
        Assertions.assertNotNull(connection.getMetadata());
        assertEquals("2", connection.getMetadata().get("region"));
        assertEquals("4", connection.getMetadata().get("race"));
        assertEquals("0", connection.getMetadata().get("league"));
        assertEquals("1", connection.getMetadata().get("rating_from"));
        assertEquals("1", connection.getMetadata().get("rating_to"));
    }

    private void stubNoManagedGuilds()
    {
        when(discordService.getDiscordAPI().getGuilds(any(), any()))
            .thenReturn(Flux.empty());
    }

    private Tuple3<Account, PlayerCharacter[], Team> stubMainTeam()
    {
        final TeamType TEAM_TYPE = TeamType.ARRANGED;
        final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;
        Region region = Region.EU;
        int curSeason = 1000;
        int lastValidSeason = curSeason - DiscordService.MAIN_TEAM_SEASON_DEPTH + 1;
        Season season1 = new Season(null, lastValidSeason, region, 2020, 1,
            SC2Pulse.offsetDateTime(2020, 1, 1), SC2Pulse.offsetDateTime(2020, 2, 1));
        Season season2 = new Season(null, curSeason, region, 2020, 2,
            SC2Pulse.offsetDateTime(2020, 2, 1), SC2Pulse.offsetDateTime(2020, 3, 1));
        //generate some useless noise
        seasonGenerator.generateSeason
        (
            List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_4V4),
            TEAM_TYPE,
            TIER_TYPE,
            1
        );
        Division bronze1 = divisionDAO.findListByLadder(season1.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE, TIER_TYPE).get(0);
        Division gold2 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.GOLD, QueueType.LOTV_4V4, TEAM_TYPE, TIER_TYPE).get(0);

        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "refacc#123"));
        //all linked characters should be checked
        PlayerCharacter[] characters = new PlayerCharacter[3];
        for(int i = 0; i < characters.length; i++)
            characters[i] = playerCharacterDAO.merge(new PlayerCharacter(
                null, account.getId(), Region.values()[i], 10000L + i, 1, "refchar#123"));

        //the last 1v1 team with the highest mmr, should be picked despite having lower MMR than
        //in the previous season
        Team team1 = seasonGenerator.createTeam
        (
            season2, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, bronze1,
            TeamLegacyId.trusted("10000"), 1L, 1, 2, 3, 4, characters[2]
        );
        //current season 1v1, should be skipped despite being a GM, only MMR matters
        Team team2 = seasonGenerator.createTeam
        (
            season2, new BaseLeague(BaseLeague.LeagueType.GRANDMASTER, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, bronze1,
            TeamLegacyId.trusted("10001"), 0L, 1, 2, 3, 4, characters[1]
        );
        //not a 1v1 team, should be skipped
        Team team3 = seasonGenerator.createTeam
        (
            season2, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TEAM_TYPE), TIER_TYPE, bronze1,
            TeamLegacyId.trusted("10002"), 2L, 1, 2, 3, 4, characters[0]
        );
        //1v1 team from the previous season, should be skipped despite high mmr
        Team team4 = seasonGenerator.createTeam
        (
            season1, new BaseLeague(BaseLeague.LeagueType.GOLD, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, gold2,
            TeamLegacyId.trusted("10003"), 3L, 1, 2, 3, 4, characters[0]
        );

        return Tuples.of(account, characters, team1);
    }

    @Test
    public void whenTeamIsTooOld_thenIgnoreIt()
    {
        final TeamType TEAM_TYPE = TeamType.ARRANGED;
        final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;
        int targetSeasonOffset = DiscordService.MAIN_TEAM_SEASON_DEPTH;
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(5);
        List<Season> seasons = new ArrayList<>();
        for(int i = 0; i <= targetSeasonOffset; i++)
            seasons.add(new Season(null, i, Region.EU, 2020, i, start.plusMonths(i), start.plusMonths(i + 1)));
        seasonGenerator.generateSeason
        (
            seasons,
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_4V4),
            TEAM_TYPE,
            TIER_TYPE,
            1
        );

        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "refacc#123"));
        PlayerCharacter character = playerCharacterDAO.merge(new PlayerCharacter(
            null, account.getId(), Region.EU, 10000L, 1, "refchar#123"));
        Season oldSeason = seasons.get(0);
        Division bronze1 = divisionDAO.findListByLadder
        (
            oldSeason.getBattlenetId(),
            Region.EU,
            BaseLeague.LeagueType.BRONZE,
            QueueType.LOTV_4V4,
            TEAM_TYPE, TIER_TYPE
        ).get(0);

        Team team1 = seasonGenerator.createTeam
        (
            oldSeason, new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TEAM_TYPE), TIER_TYPE, bronze1,
            TeamLegacyId.trusted("10000"), 1L, 1, 2, 3, 4, character
        );

        assertTrue(discordService.findMainTeam(account.getId()).isEmpty());
    }

    @Test
    public void testFindAccountIds()
    {
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "refacc", 3);
        DiscordUser[] discordUsers = discordUserDAO.merge(new LinkedHashSet<>(List.of(
            new DiscordUser(Snowflake.of(1L), "name", 1),
            new DiscordUser(Snowflake.of(2L), "name", 2),
            new DiscordUser(Snowflake.of(3L), "name", 3)
        )))
            .toArray(DiscordUser[]::new);
        assertTrue(accountDiscordUserDAO.findAccountIds().isEmpty());

        accountDiscordUserDAO.create(Set.of(
            new AccountDiscordUser(accounts[0].getId(), discordUsers[0].getId()),
            new AccountDiscordUser(accounts[1].getId(), discordUsers[1].getId())
        ));
        Set<Long> accountIds1 = accountDiscordUserDAO.findAccountIds();
        assertEquals(2, accountIds1.size());
        assertTrue(accountIds1.contains(accounts[0].getId()));
        assertTrue(accountIds1.contains(accounts[1].getId()));

        accountDiscordUserDAO.remove(accounts[0].getId(), discordUsers[0].getId());
        Set<Long> accountIds2 = accountDiscordUserDAO.findAccountIds();
        assertEquals(1, accountIds2.size());
        assertTrue(accountIds2.contains(accounts[1].getId()));
    }

    @Test
    public void testExistsByAccountId()
    {
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "refacc", 2);
        DiscordUser[] discordUsers = discordUserDAO.merge(new LinkedHashSet<>(List.of(
            new DiscordUser(Snowflake.of(10L), "name", 1),
            new DiscordUser(Snowflake.of(11L), "name", 2)
        )))
            .toArray(DiscordUser[]::new);
        accountDiscordUserDAO.create(Set.of(
            new AccountDiscordUser(accounts[0].getId(), discordUsers[0].getId())
        ));
        assertTrue(accountDiscordUserDAO.existsByAccountId(accounts[0].getId()));
        assertFalse(accountDiscordUserDAO.existsByAccountId(accounts[1].getId()));
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = BATTLE_TAG)
    public void testRemoveWithoutOauth2Permissions()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "refacc", 2);
        DiscordUser[] discordUsers = discordUserDAO.merge(new LinkedHashSet<>(List.of(
            new DiscordUser(Snowflake.of(10L), "name", 1),
            new DiscordUser(Snowflake.of(11L), "name", 2)
        )))
            .toArray(DiscordUser[]::new);
        accountDiscordUserDAO.create(Set.of(
            new AccountDiscordUser(accounts[0].getId(), discordUsers[0].getId()),
            new AccountDiscordUser(accounts[1].getId(), discordUsers[1].getId())
        ));
        OAuth2AuthorizedClient client = WebServiceTestUtil.createOAuth2AuthorizedClient
        (
            clientRegistrationRepository
                .findByRegistrationId(DiscordAPI.USER_CLIENT_REGISTRATION_ID),
            String.valueOf(accounts[0].getId())
        );
        oAuth2AuthorizedClientService.saveAuthorizedClient(client, authentication);
        stubNoManagedGuilds();
        when(stubGatewayClient(discordService.getDiscordAPI()).getGuilds()).thenReturn(Flux.empty());

        assertTrue(accountDiscordUserDAO.existsByAccountId(accounts[0].getId()));
        assertTrue(accountDiscordUserDAO.existsByAccountId(accounts[1].getId()));
        discordService.updateRolesAndUnlinkUsersWithoutOauth2Permissions();
        //discord user link with oauth2 client is left untouched
        assertTrue(accountDiscordUserDAO.existsByAccountId(accounts[0].getId()));
        //discord user link without oauth2 client is removed
        assertFalse(accountDiscordUserDAO.existsByAccountId(accounts[1].getId()));
    }

    public static GatewayDiscordClient stubGatewayClient(DiscordAPI api)
    {
        SpringDiscordClient springDiscordClient = mock(SpringDiscordClient.class);
        GatewayDiscordClient gatewayDiscordClient = mock(GatewayDiscordClient.class);
        when(api.getDiscordClient()).thenReturn(springDiscordClient);
        when(springDiscordClient.getClient()).thenReturn(gatewayDiscordClient);
        return gatewayDiscordClient;
    }


    private static void assertDeepEquals(DiscordIdentity user1, DiscordIdentity user2)
    {
        assertNotNull(user1);
        assertNotNull(user2);
        assertEquals(user1.getName(), user2.getName());
        assertEquals(user1.getDiscriminator(), user2.getDiscriminator());
    }

    public static void verifyStdDiscordUser(DiscordUser user, int base)
    {
        assertEquals(Snowflake.of(base), user.getId());
        assertEquals("name" + base, user.getName());
        assertEquals(base, user.getDiscriminator());
    }

}
