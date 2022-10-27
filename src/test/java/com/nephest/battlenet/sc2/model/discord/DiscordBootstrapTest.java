// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildEmojiStore;
import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import com.nephest.battlenet.sc2.discord.event.UserCommand;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class DiscordBootstrapTest
{

    @Mock
    private GuildEmojiStore guildEmojiStore;

    @Mock
    private UpdateService updateService;

    @Mock
    private WebContextUtil webContextUtil;

    private DiscordBootstrap discordBootstrap;

    @BeforeEach
    public void beforeEach()
    {
        when(webContextUtil.getPublicUrl()).thenReturn("publicUrl");
        discordBootstrap = new DiscordBootstrap
        (
            Map.of(),
            Map.of(),
            guildEmojiStore,
            updateService,
            webContextUtil
        );
    }

    @Test
    public void testGenerateCharacterURL()
    {
        String expected = "[**[proTeam1]proName1** | [clan1]name | tag#1 | "
            + DiscordBootstrap.SC2_REVEALED_TAG + "]"
            + "(<publicUrl?type=character&id=1&m=1#player-stats-mmr>)";
        LadderTeamMember member = new LadderTeamMember
        (
            new Account(2L, Partition.GLOBAL, "tag#1"),
            new PlayerCharacter(1L, 2L, Region.EU, 2L, 2, "name#1"),
            new Clan(2, "clan1", Region.EU, "clanName"),
            "proName1", "proTeam1",
            null,
            1, 1, 1, 1
        );
        assertEquals(expected, discordBootstrap.generateCharacterURL(member));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void whenRegisteringCommands_registerAllAtOnce()
    {
        SlashCommand slashCommand = mock(SlashCommand.class);
        when(slashCommand.generateCommandRequest())
            .thenReturn(ImmutableApplicationCommandRequest.builder().name("slash"));
        UserCommand userCommand = mock(UserCommand.class);
        when(userCommand.generateCommandRequest())
            .thenReturn(ImmutableApplicationCommandRequest.builder().name("user"));

        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.on(any(), any())).thenReturn(Flux.empty());
        when(client.updatePresence(any())).thenReturn(Mono.empty());
        RestClient restClient = mock(RestClient.class);
        when(client.getRestClient()).thenReturn(restClient);
        ApplicationService applicationService = mock(ApplicationService.class);
        when(restClient.getApplicationId()).thenReturn(Mono.just(3L));
        when(applicationService.bulkOverwriteGlobalApplicationCommand(eq(3L), any()))
            .thenReturn(Flux.empty());
        when(restClient.getApplicationService()).thenReturn(applicationService);

        DiscordBootstrap.load
        (
            List.of(slashCommand),
            List.of(userCommand),
            List.of(),
            guildEmojiStore,
            client,
            null
        );

        ArgumentCaptor<List<ApplicationCommandRequest>> requestCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(applicationService, times(1))
            .bulkOverwriteGlobalApplicationCommand(eq(3L), requestCaptor.capture());
        List<ApplicationCommandRequest> registeredRequests = requestCaptor.getValue();
        assertEquals(2, registeredRequests.size());
        assertTrue(registeredRequests.stream().anyMatch(r->r.name().equals("slash")));
        assertTrue(registeredRequests.stream().anyMatch(r->r.name().equals("user")));
    }

}
