// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.DiscordService.DB_CURSOR_BATCH_SIZE;
import static com.nephest.battlenet.sc2.web.service.DiscordService.USER_UPDATE_BATCH_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.discord.connection.PulseConnectionParameters;
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
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import reactor.core.publisher.Flux;

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
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Mock
    private ExecutorService executor;

    @Mock
    private ConversionService conversionService;

    private DiscordService discordService;

    @BeforeEach
    public void beforeEach()
    {
        discordService = new DiscordService
        (
            discordUserDAO,
            accountDiscordUserDAO,
            seasonDAO,
            accountDAO,
            playerCharacterDAO,
            ladderSearchDAO,
            api,
            oAuth2AuthorizedClientService,
            new PulseConnectionParameters(conversionService),
            executor,
            conversionService
        );
    }

    public void stubExecutor()
    {
        when(executor.submit(any(Runnable.class))).then(i->{
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
        when(discordUserDAO.findIdsByIdCursor(0L, DB_CURSOR_BATCH_SIZE))
            .thenReturn
            (
                LongStream.range(0, DB_CURSOR_BATCH_SIZE)
                    .boxed()
                    .collect(Collectors.toList())
            );
        //2 db batch, half
        when(discordUserDAO.findIdsByIdCursor(DB_CURSOR_BATCH_SIZE - 1L, DB_CURSOR_BATCH_SIZE))
            .thenReturn
            (
                LongStream.range(DB_CURSOR_BATCH_SIZE, DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2)
                    .boxed()
                    .collect(Collectors.toList())
            );
        //3 db batch, empty
        when(discordUserDAO.findIdsByIdCursor(
            DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2 - 1L, DB_CURSOR_BATCH_SIZE))
                .thenReturn(List.of());

        //web batches
        when(api.getUsers(any())).thenAnswer(inv->
            Flux.fromIterable(inv.getArgument(0))
                .map(f->(Long) f)
                .map(l->new DiscordUser(l, "name" + l, l.intValue())));

        discordService.update();
        verify(discordUserDAO).removeUsersWithNoAccountLinked();

        ArgumentCaptor<DiscordUser> userCaptor = ArgumentCaptor.forClass(DiscordUser.class);
        double userBatches = Math.ceil(DB_CURSOR_BATCH_SIZE / (double) USER_UPDATE_BATCH_SIZE)
            +  Math.ceil(Math.ceil(DB_CURSOR_BATCH_SIZE / 2.0d) / USER_UPDATE_BATCH_SIZE);
        int batchCount = (int) userBatches;
        //users are saved in batches
        verify(discordUserDAO, times(batchCount)).merge(userCaptor.capture());

        //all users are saved
        List<DiscordUser> users = userCaptor.getAllValues();
        assertEquals(DB_CURSOR_BATCH_SIZE + DB_CURSOR_BATCH_SIZE / 2, users.size());
        for(int i = 0; i < users.size(); i++)
        {
            DiscordUser user = users.get(i);
            assertEquals(i, user.getId());
            assertEquals("name" + i, user.getName());
            assertEquals(i, user.getDiscriminator());
        }
    }

    @Test
    public void whenNoMainTeam_thenDropRoles()
    {
        DiscordService spy = Mockito.spy(discordService);
        when(spy.findMainTeam(any())).thenReturn(Optional.empty());
        String tag = "tag#123";
        Account account = new Account(1L, Partition.GLOBAL, tag);
        when(accountDAO.findByIds(1L)).thenReturn(List.of(account));
        ArgumentCaptor<ApplicationRoleConnection> connectionArgumentCaptor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);

        spy.updateRoles(1L);
        verify(api).updateConnectionMetaData(eq("1"), connectionArgumentCaptor.capture());
        ApplicationRoleConnection connection = connectionArgumentCaptor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(tag, connection.getPlatformUsername());
        assertNull(connection.getMetadata());
    }

    @Test
    public void whenMainTeaExists_thenUpdateRoles()
    {
        when(conversionService.convert(Region.KR, Integer.class)).thenReturn(3);
        when(conversionService.convert(BaseLeague.LeagueType.DIAMOND, Integer.class)).thenReturn(4);
        when(conversionService.convert(Race.PROTOSS, Integer.class)).thenReturn(2);
        DiscordService spy = Mockito.spy(discordService);
        String tag = "tag#123";
        LadderTeam team = new LadderTeam
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
            BigInteger.ONE,
            1,
            1234L, 1, 1, 1, 1,
            List.of
            (
                new LadderTeamMember
                (
                    new Account(2L, Partition.GLOBAL, "tag2#123"),
                    null, null, null, null, null,
                    2, 1, 1, 1
                ),
                new LadderTeamMember
                (
                    new Account(1L, Partition.GLOBAL, tag),
                    null, null, null, null, null,
                    1, 2, 1, 1
                )
            ),
            null
        );

        when(spy.findMainTeam(any())).thenReturn(Optional.of(team));
        ArgumentCaptor<ApplicationRoleConnection> connectionArgumentCaptor =
            ArgumentCaptor.forClass(ApplicationRoleConnection.class);

        spy.updateRoles(1L);
        verify(api).updateConnectionMetaData(eq("1"), connectionArgumentCaptor.capture());
        ApplicationRoleConnection connection = connectionArgumentCaptor.getValue();
        assertEquals(ApplicationRoleConnection.DEFAULT_PLATFORM_NAME, connection.getPlatformName());
        assertEquals(tag, connection.getPlatformUsername());
        assertNotNull(connection.getMetadata());
        assertEquals("3", connection.getMetadata().get("region"));
        assertEquals("2", connection.getMetadata().get("race"));
        assertEquals("4", connection.getMetadata().get("league"));
        assertEquals("1234", connection.getMetadata().get("rating_from"));
        assertEquals("1234", connection.getMetadata().get("rating_to"));
    }

}
