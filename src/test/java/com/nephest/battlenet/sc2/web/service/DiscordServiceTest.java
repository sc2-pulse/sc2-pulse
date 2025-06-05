// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.DiscordService.DB_CURSOR_BATCH_SIZE;
import static com.nephest.battlenet.sc2.web.service.DiscordService.USER_UPDATE_BATCH_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.IdentifiableEntity;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.discord.SpringDiscordClient;
import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.discord.connection.PulseConnectionParameters;
import com.nephest.battlenet.sc2.discord.event.RolesSlashCommand;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.web.util.MonoUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
public class DiscordServiceTest
{

    @Mock
    private DiscordUserDAO discordUserDAO;

    @Mock
    private AccountDiscordUserDAO accountDiscordUserDAO;

    @Mock
    private SeasonDAO seasonDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private PlayerCharacterDAO playerCharacterDAO;

    @Mock
    private LadderSearchDAO ladderSearchDAO;

    @Mock
    private DiscordAPI api;

    @Mock
    private GuildRoleStore guildRoleStore;

    @Mock
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Mock
    private ExecutorService executor;

    @Mock
    private EventService eventService;

    @Mock
    private ConversionService conversionService;

    @Mock
    private RolesSlashCommand rolesSlashCommand;

    @Captor
    private ArgumentCaptor<Set<DiscordUser>> userCaptor;

    private DiscordService discordService;

    @BeforeEach
    public void beforeEach()
    {
        when(eventService.getLadderCharacterActivityEvent()).thenReturn(Flux.empty());
        discordService = new DiscordService
        (
            discordUserDAO,
            accountDiscordUserDAO,
            seasonDAO,
            accountDAO,
            playerCharacterDAO,
            ladderSearchDAO,
            api,
            guildRoleStore,
            oAuth2AuthorizedClientService,
            eventService,
            new PulseConnectionParameters(conversionService),
            executor,
            conversionService
        );
        discordService.setDiscordService(discordService);
        discordService.setRolesSlashCommand(rolesSlashCommand);
    }

    public void stubExecutor()
    {
        lenient().when(executor.submit(any(Runnable.class))).then(i->{
            Runnable r = i.getArgument(0);
            r.run();
            return CompletableFuture.completedFuture(null);
        });
        lenient().when(executor.submit(any(Runnable.class), any())).then(i->{
            Runnable r = i.getArgument(0);
            r.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    @Test
    public void whenEmptyBatch_thenRemoveEmptyCharactersAndDoNothing()
    {
        when(discordUserDAO.findIdsByIdCursor(any(), anyInt())).thenReturn(List.of());
        discordService.update();

        verify(discordUserDAO).removeUsersWithNoAccountLinked();
        verifyNoMoreInteractions(discordUserDAO);
    }

    @Test
    public void whenBatchUpdate_thenUpdateInBatches()
    {
        stubExecutor();
        //1 db batch
        when(discordUserDAO.findIdsByIdCursor(Snowflake.of(0L), DB_CURSOR_BATCH_SIZE))
            .thenReturn
            (
                LongStream.range(0, DB_CURSOR_BATCH_SIZE)
                    .boxed()
                    .map(Snowflake::of)
                    .collect(Collectors.toList())
            );
        //2 db batch, half
        when(discordUserDAO.findIdsByIdCursor(Snowflake.of(DB_CURSOR_BATCH_SIZE - 1L), DB_CURSOR_BATCH_SIZE))
            .thenReturn
            (
                LongStream.range(DB_CURSOR_BATCH_SIZE, DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2)
                    .boxed()
                    .map(Snowflake::of)
                    .collect(Collectors.toList())
            );
        //3 db batch, empty
        when(discordUserDAO.findIdsByIdCursor(
                Snowflake.of(DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2 - 1L), DB_CURSOR_BATCH_SIZE))
                .thenReturn(List.of());

        //web batches
        when(api.getUsers(any())).thenAnswer(inv->
            Flux.fromIterable(inv.getArgument(0))
                .map(f->(Snowflake) f)
                .map(id->new DiscordUser(id, "name" + id.asLong(), (int) id.asLong())));

        discordService.update();
        verify(discordUserDAO).removeUsersWithNoAccountLinked();

        double userBatches = Math.ceil(DB_CURSOR_BATCH_SIZE / (double) USER_UPDATE_BATCH_SIZE)
            +  Math.ceil(Math.ceil(DB_CURSOR_BATCH_SIZE / 2.0d) / USER_UPDATE_BATCH_SIZE);
        int batchCount = (int) userBatches;
        //users are saved in batches
        verify(discordUserDAO, times(batchCount)).merge(userCaptor.capture());

        //all users are saved
        List<DiscordUser> users = userCaptor.getAllValues()
            .stream()
            .flatMap(Collection::stream)
            .sorted(Comparator.comparing(u->u.getId().asLong()))
            .collect(Collectors.toList());
        assertEquals(DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2, users.size());
        for(int i = 0; i < users.size(); i++)
        {
            DiscordUser user = users.get(i);
            assertEquals(Snowflake.of(i), user.getId());
            assertEquals("name" + i, user.getName());
            assertEquals(i, user.getDiscriminator());
        }
    }

    @Test
    public void whenNoMainTeam_thenDropRoles()
    {
        when(accountDiscordUserDAO.findAccountIds()).thenReturn(Set.of(1L));
        DiscordService spy = Mockito.spy(discordService);
        when(spy.findMainTeam(any())).thenReturn(Optional.empty());
        String tag = "tag#123";
        Account account = new Account(1L, Partition.GLOBAL, tag);
        when(accountDAO.findByIds(Set.of(1L))).thenReturn(List.of(account));
        PulseMappings<Role> roleMappings = PulseMappings.empty();
        Tuple2<Guild, Member> member1 = stubRoleMember(roleMappings);
        Tuple2<Guild, Member> member2 = stubRoleMember(roleMappings);
        OAuth2AuthorizedClient client = WebServiceTestUtil
            .createOAuth2AuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1");
        when(api.getAuthorizedClient(1L)).thenReturn(Optional.of(client));
        doReturn(Flux.just(member1.getT1(), member2.getT1())).when(spy).getManagedRoleGuilds(client);
        Tuple2<Mono<Void>, Mono<Void>> rolesMono = MonoUtil.verifiableMono();
        when(rolesSlashCommand.updateRoles(any(), any(), any(), any(), any()))
            .thenReturn(new ImmutableTriple<>(null, Set.of(), rolesMono.getT1().flux()));
        doReturn(Mono.empty()).when(api).updateConnectionMetaData(any(), any());
        ArgumentCaptor<ApplicationRoleConnection> connectionArgumentCaptor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);

        spy.updateRoles(1L).blockLast();

        //Application connection metadata is dropped
        verify(api).updateConnectionMetaData(eq(client), connectionArgumentCaptor.capture());
        ApplicationRoleConnection connection = connectionArgumentCaptor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(tag, connection.getPlatformUsername());
        assertNull(connection.getMetadata());

        //members in all guilds are dropped
        verify(rolesSlashCommand).updateRoles
        (
            null,
            null,
            roleMappings,
            member1.getT2(),
            "Updated roles based on the last ranked ladder stats"
        );
        verify(rolesSlashCommand).updateRoles
        (
            null,
            null,
            roleMappings,
            member2.getT2(),
            "Updated roles based on the last ranked ladder stats"
        );
        rolesMono.getT2().block();
    }

    @Test
    public void whenNoOauth2Client_thenRevokeRolesAndUnlink()
    {
        when(accountDiscordUserDAO.findAccountIds()).thenReturn(Set.of(1L));
        verifyRevokeRoles(DiscordService::updateRolesAndUnlinkUsersWithoutOauth2Permissions);
        verify(accountDiscordUserDAO).remove(1L, null);
    }

    @Test
    public void whenDropRevokedClient_thenRevokeInstead()
    {
        verifyRevokeRoles(ds->ds.updateRoles(null, DiscordService.RoleUpdateMode.DROP).blockLast());
    }

    private void verifyRevokeRoles(Consumer<DiscordService> consumer)
    {
        DiscordService spy = Mockito.spy(discordService);
        consumer.accept(spy);
        //Don't update roles when revoking
        verify(api, never()).updateConnectionMetaData(any());
        verifyNoInteractions(rolesSlashCommand);
    }

    @Test
    public void whenMainTeaExists_thenUpdateRoles()
    {
        when(conversionService.convert(Region.KR, Integer.class)).thenReturn(3);
        when(conversionService.convert(BaseLeague.LeagueType.DIAMOND, Integer.class)).thenReturn(4);
        when(conversionService.convert(Race.PROTOSS, Integer.class)).thenReturn(2);
        when(accountDiscordUserDAO.findAccountIds()).thenReturn(Set.of(1L));
        DiscordService spy = Mockito.spy(discordService);
        String tag = "tag#123";
        LadderTeam team = LadderTeam.joined
        (
            1L,
            1,
            Region.KR,
            new BaseLeague
            (
                BaseLeague.LeagueType.DIAMOND,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.trusted("1"),
            1,
            1234L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime(),
            List.of
            (
                new LadderTeamMember
                (
                    new Account(2L, Partition.GLOBAL, "tag2#123"),
                    null, null, null, null, null, null,
                    2, 1, 1, 1
                ),
                new LadderTeamMember
                (
                    new Account(1L, Partition.GLOBAL, tag),
                    null, null, null, null, null, null,
                    1, 2, 1, 1
                )
            ),
            null
        );

        when(spy.findMainTeam(any())).thenReturn(Optional.of(team));
        PulseMappings<Role> roleMappings = PulseMappings.empty();
        Tuple2<Guild, Member> member1 = stubRoleMember(roleMappings);
        Tuple2<Guild, Member> member2 = stubRoleMember(roleMappings);
        OAuth2AuthorizedClient client = WebServiceTestUtil
            .createOAuth2AuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1");
        when(api.getAuthorizedClient(1L)).thenReturn(Optional.of(client));
        doReturn(Flux.just(member1.getT1(), member2.getT1())).when(spy).getManagedRoleGuilds(client);
        Tuple2<Mono<Void>, Mono<Void>> rolesMono = MonoUtil.verifiableMono();
        when(rolesSlashCommand.updateRoles(any(), any(), any(), any(), any()))
            .thenReturn(new ImmutableTriple<>(team, Set.of(), rolesMono.getT1().flux()));
        doReturn(Mono.empty()).when(api).updateConnectionMetaData(any(), any());
        ArgumentCaptor<ApplicationRoleConnection> connectionArgumentCaptor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);

        spy.updateRoles(1L).blockLast();

        //Application connection metadata is updated
        verify(api).updateConnectionMetaData(eq(client), connectionArgumentCaptor.capture());
        ApplicationRoleConnection connection = connectionArgumentCaptor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(tag, connection.getPlatformUsername());
        assertNotNull(connection.getMetadata());
        assertEquals("3", connection.getMetadata().get("region"));
        assertEquals("2", connection.getMetadata().get("race"));
        assertEquals("4", connection.getMetadata().get("league"));
        assertEquals("1234", connection.getMetadata().get("rating_from"));
        assertEquals("1234", connection.getMetadata().get("rating_to"));

        //members in all guilds are updated
        verify(rolesSlashCommand).updateRoles
        (
            team,
            Race.PROTOSS,
            roleMappings,
            member1.getT2(),
            "Updated roles based on the last ranked ladder stats"
        );
        verify(rolesSlashCommand).updateRoles
        (
            team,
            Race.PROTOSS,
            roleMappings,
            member2.getT2(),
            "Updated roles based on the last ranked ladder stats"
        );
        rolesMono.getT2().block();
    }

    private Tuple2<Guild, Member> stubRoleMember(PulseMappings<Role> roleMappings)
    {
        Member member1 = mock(Member.class);
        Guild guild1 = mock(Guild.class);
        when(discordUserDAO.findByAccountId(1L, false))
            .thenReturn(Optional.of(new DiscordUser(Snowflake.of(123L), "name", 1)));
        when(guild1.getMemberById(argThat(s->s.asLong() == 123L))).thenReturn(Mono.just(member1));
        when(guildRoleStore.getManagedRoleMappings(guild1)).thenReturn(Mono.just(roleMappings));
        return Tuples.of(guild1, member1);
    }

    @Test
    public void whenDiscordUserNotBound_thenDontUpdateRoles()
    {
        when(accountDiscordUserDAO.findAccountIds()).thenReturn(Set.of(2L));
        discordService.updateRoles(1L);
        verifyNoMoreInteractions(api);
    }

    @CsvSource
    ({
        "true, true, true, true",
        "true, true, false, false",
        "true, false, false, false",
        "false, false, false, false",
        "false, false, true, false",
        "false, true, false, false"
    })
    @ParameterizedTest
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testGetManagedRoleGuilds
    (
        boolean managedByBot,
        boolean hasPermissions,
        boolean hasRoles,
        boolean expectedResult
    )
    {
        Guild guild = mock(Guild.class);
        Snowflake guildID = Snowflake.of(10L);
        when(guild.getId()).thenReturn(guildID);
        OAuth2AuthorizedClient oauth2Client = WebServiceTestUtil
            .createOAuth2AuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1");
        when(api.getAuthorizedClient(1L)).thenReturn(Optional.of(oauth2Client));
        when(api.getGuilds(oauth2Client, IdentifiableEntity.class))
            .thenReturn(Flux.just(new IdentifiableEntity(guildID)));

        when(api.getBotGuilds()).thenReturn(Map.of(managedByBot ? guildID : Snowflake.of(guildID.asLong()  + 1), guild));
        GatewayDiscordClient client = mockClient();
        when(client.getGuildById(guildID)).thenReturn(Mono.just(guild));

        Member self = mock(Member.class);
        when(self.getBasePermissions())
            .thenReturn(Mono.just(PermissionSet.of(hasPermissions ? Permission.MANAGE_ROLES : Permission.EMBED_LINKS)));
        when(guild.getSelfMember()).thenReturn(Mono.just(self));

        Role role = mock(Role.class);
        PulseMappings<Role> mappings = hasRoles
            ?
                new PulseMappings<>
                (
                    Map.of(Region.EU, List.of(role)),
                    Map.of(), Map.of(), Map.of(), Objects::toString, ""
                )
            : PulseMappings.empty();
        when(guildRoleStore.getManagedRoleMappings(guild)).thenReturn(Mono.just(mappings));

        Guild result = discordService.getManagedRoleGuilds(oauth2Client).blockLast();
        assertEquals(expectedResult, result != null);
    }

    private GatewayDiscordClient mockClient()
    {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        SpringDiscordClient springClient = mock(SpringDiscordClient.class);
        when(api.getDiscordClient()).thenReturn(springClient);
        when(springClient.getClient()).thenReturn(client);
        return client;
    }


}
