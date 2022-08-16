// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class Summary1v1UserCommandTest
{

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private Summary1v1Command cmdd;

    @Mock
    private User user;

    @Mock
    private Interaction interaction;

    @Mock
    private UserInteractionEvent evt;

    private Summary1v1UserCommand cmd;

    @BeforeEach
    public void beforeEach()
    {
        cmd = new Summary1v1UserCommand(accountDAO, cmdd);
    }

    @Test
    public void testHandle()
    {
        when(user.getId()).thenReturn(Snowflake.of(321L));
        Member member = mock(Member.class);
        when(member.getDisplayName()).thenReturn("displayName123");
        when(user.getUsername()).thenReturn("name123");
        when(user.asMember(any())).thenReturn(Mono.just(member));
        when(interaction.getGuildId()).thenReturn(Optional.of(Snowflake.of(1L)));
        when(evt.getResolvedUser()).thenReturn(user);
        when(evt.getInteraction()).thenReturn(interaction);
        when(accountDAO.findByDiscordUserId(321L)).thenReturn(Optional.empty());

        cmd.handle(evt);
        verify(cmdd).handle(evt, null, null, Summary1v1Command.DEFAULT_DEPTH, "displayName123", "name123");
    }

    @Test
    public void whenLinkedDiscordUser_thenUseBattleTag()
    {
        when(user.getId()).thenReturn(Snowflake.of(321L));
        when(evt.getResolvedUser()).thenReturn(user);
        when(accountDAO.findByDiscordUserId(321L))
            .thenReturn(Optional.of(new Account(1L, Partition.GLOBAL, "tag#1")));

        cmd.handle(evt);
        verify(cmdd).handle(evt, null, null, Summary1v1Command.DEFAULT_DEPTH, "tag#1");
    }


    @CsvSource
    ({
        "[IMRTS]波哥 , 波哥",
        "name name2, name2",
        "name123_, name123",
        "name123, name123"
    })
    @ParameterizedTest
    public void testSanitizeName(String name, String expectedResult)
    {
        assertEquals(expectedResult, Summary1v1UserCommand.sanitizeName(name));
    }

}
