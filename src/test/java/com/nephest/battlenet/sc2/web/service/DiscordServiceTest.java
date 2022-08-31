// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.DiscordService.DB_CURSOR_BATCH_SIZE;
import static com.nephest.battlenet.sc2.web.service.DiscordService.USER_UPDATE_BATCH_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class DiscordServiceTest
{

    @Mock
    private DiscordUserDAO discordUserDAO;

    @Mock
    private AccountDiscordUserDAO accountDiscordUserDAO;

    @Mock
    private DiscordAPI api;

    @Mock
    private ExecutorService executor;

    private DiscordService discordService;

    @BeforeEach
    public void beforeEach()
    {
        discordService = new DiscordService(discordUserDAO, accountDiscordUserDAO, api, executor);
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
            Flux.fromIterable((Iterable<Long>) inv.getArguments()[0])
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

}
