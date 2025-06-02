// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.discord.event.UserCommandTest;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class Summary1v1UserCommandTest
{

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private Summary1v1Command cmdd;

    @Mock
    private DiscordBootstrap discordBootstrap;

    @Mock
    private User user;

    @Mock
    private UserInteractionEvent evt;

    private Summary1v1UserCommand cmd;

    @BeforeEach
    public void beforeEach()
    {
        cmd = new Summary1v1UserCommand(accountDAO, cmdd, discordBootstrap);
    }

    @CsvSource
    ({
        "321, 1, true, ':white_check_mark: Verified'",
        "321, 321, true, ':white_check_mark: Verified'",

        "321, 1, false, ':grey_question: Unverified, searching by discord username'",
        "321, 321, false, ':grey_question: Unverified, searching by discord username"
            + "(publicUrl/verify)'"
    })
    @ParameterizedTest
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testHandle
    (
        long resolvedUserId,
        long interactionUserId,
        boolean verified,
        String additionalDescription
    )
    {
        when(discordBootstrap.getAccountVerificationLink()).thenReturn("publicUrl/verify");
        cmd = new Summary1v1UserCommand(accountDAO, cmdd, discordBootstrap);
        UserCommandTest.stubUserInteractionUsers(evt, resolvedUserId, interactionUserId);
        User resolvedUser = evt.getResolvedUser();
        when(resolvedUser.getUsername()).thenReturn("name123");
        when(evt.getInteraction().getGuildId()).thenReturn(Optional.of(Snowflake.of(1L)));
        when(discordBootstrap.getTargetDisplayNameOrName(evt))
            .thenReturn(Mono.just("displayName123"));

        when(accountDAO.findByDiscordUserId(resolvedUserId)).thenReturn
        (
            verified
                ? Optional.of(new Account(resolvedUserId, Partition.GLOBAL, "tag#1"))
                : Optional.empty()
        );

        cmd.handle(evt);
        verify(cmdd).handle
        (
            evt,
            additionalDescription,
            null,
            null,
            Summary1v1Command.DEFAULT_DEPTH,
            verified ? new String[]{"tag#1"} : new String[]{"displayName123", "name123"}
        );
    }

    @Test
    public void whenLinkedDiscordUser_thenUseBattleTag()
    {
        when(user.getId()).thenReturn(Snowflake.of(321L));
        when(evt.getResolvedUser()).thenReturn(user);
        when(accountDAO.findByDiscordUserId(321L))
            .thenReturn(Optional.of(new Account(1L, Partition.GLOBAL, "tag#1")));

        cmd.handle(evt);
        verify(cmdd).handle
        (
            evt,
            ":white_check_mark: Verified",
            null,
            null,
            Summary1v1Command.DEFAULT_DEPTH,
            "tag#1"
        );
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
