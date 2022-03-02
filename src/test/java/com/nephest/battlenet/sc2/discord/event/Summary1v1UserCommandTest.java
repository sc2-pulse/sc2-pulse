// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Summary1v1UserCommandTest
{

    @Mock
    private Summary1v1Command cmdd;

    @Test
    public void testHandle()
    {
        Summary1v1UserCommand cmd = new Summary1v1UserCommand(cmdd);
        User user = mock(User.class);
        Member member = mock(Member.class);
        when(member.getDisplayName()).thenReturn("displayName123");
        when(user.getUsername()).thenReturn("name123");
        when(user.asMember(any())).thenReturn(Mono.just(member));
        Interaction interaction = mock(Interaction.class);
        when(interaction.getGuildId()).thenReturn(Optional.of(Snowflake.of(1L)));
        UserInteractionEvent evt = mock(UserInteractionEvent.class);
        when(evt.getResolvedUser()).thenReturn(user);
        when(evt.getInteraction()).thenReturn(interaction);

        cmd.handle(evt);
        verify(cmdd).handle(evt, null, null, Summary1v1Command.DEFAULT_DEPTH, "displayName123", "name123");
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
