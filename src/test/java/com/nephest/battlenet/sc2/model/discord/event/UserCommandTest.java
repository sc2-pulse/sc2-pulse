// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.event.UserCommand;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class UserCommandTest
{

    @CsvSource
    ({
        "1, 1, true",
        "1, 2, false"
    })
    @ParameterizedTest
    public void testIsInvokedOnSelf(long resolvedUserId, long interactionUserId, boolean expectedResult)
    {
        UserInteractionEvent evt = mock(UserInteractionEvent.class);
        stubUserInteractionUsers(evt, resolvedUserId, interactionUserId);

        assertEquals(expectedResult, UserCommand.isInvokedOnSelf(evt));
    }

    public static void stubUserInteractionUsers
    (
        UserInteractionEvent evt,
        long resolvedUserId,
        long interactionUserId
    )
    {
        User resolvedUser = mockUser(resolvedUserId);
        when(evt.getResolvedUser()).thenReturn(resolvedUser);

        Interaction interaction = mock(Interaction.class);
        User interactionUser = mockUser(interactionUserId);
        when(interaction.getUser()).thenReturn(interactionUser);
        when(evt.getInteraction()).thenReturn(interaction);
    }

    public static User mockUser(long id)
    {
        User user = mock(User.class);
        when(user.getId()).thenReturn(Snowflake.of(id));
        return user;
    }

}
