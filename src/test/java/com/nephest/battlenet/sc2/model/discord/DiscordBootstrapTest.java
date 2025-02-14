// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildEmojiStore;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import com.nephest.battlenet.sc2.discord.event.UserCommand;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        when(webContextUtil.getCharacterUrlTemplate()).thenReturn("characterUrlTemplate");
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
            + "(<characterUrlTemplate#player-stats-mmr>)";
        LadderTeamMember member = new LadderTeamMember
        (
            new Account(2L, Partition.GLOBAL, "tag#1"),
            new PlayerCharacter(1L, 2L, Region.EU, 2L, 2, "name#1"),
            new Clan(2, "clan1", Region.EU, "clanName"),
            1L, "proName1", "proTeam1",
            null,
            1, 1, 1, 1
        );
        assertEquals(expected, discordBootstrap.generateCharacterURL(member));
    }

    private InteractionCreateEvent stubEmptyGuildEvent()
    {
        Interaction interaction = mock(Interaction.class);
        when(interaction.getGuildId()).thenReturn(Optional.empty());
        InteractionCreateEvent evt = mock(InteractionCreateEvent.class);
        when(evt.getInteraction()).thenReturn(interaction);
        return evt;
    }

    @Test
    public void testGenerateRaceCharacterURL()
    {
        String expected = "Protoss [**[proTeam1]proName1** | [clan1]name | tag#1 | "
            + DiscordBootstrap.SC2_REVEALED_TAG + "]"
            + "(<characterUrlTemplate#player-stats-mmr>)";
        LadderTeamMember member = new LadderTeamMember
        (
            new Account(2L, Partition.GLOBAL, "tag#1"),
            new PlayerCharacter(1L, 2L, Region.EU, 2L, 2, "name#1"),
            new Clan(2, "clan1", Region.EU, "clanName"),
            1L, "proName1", "proTeam1",
            null,
            1, 2, 1, 1
        );
        InteractionCreateEvent evt = stubEmptyGuildEvent();
        assertEquals(expected, discordBootstrap.generateRaceCharacterURL(member, evt));
    }

    @Test
    public void testRenderLadderTeam()
    {
        String expected = "Terran [**name** | tag#2](<characterUrlTemplate#player-stats-mmr>), "
            + "Protoss [**[proTeam1]proName1** | [clan1]name | tag#1 | "
            + DiscordBootstrap.SC2_REVEALED_TAG + "]"
            + "(<characterUrlTemplate#player-stats-mmr>)\n"
            + "\uD83C\uDDEA\uD83C\uDDFA bronze ` 123` 10";
        LadderTeam team = new LadderTeam
        (
            1L, 1, Region.EU,
            new BaseLeague
            (
                BaseLeague.LeagueType.BRONZE,
                QueueType.LOTV_2V2,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            "1",
            1,
            10L,
            120, 1, 2, 2,
            SC2Pulse.offsetDateTime(),
            List.of
            (
                new LadderTeamMember
                (
                    new Account(987L, Partition.GLOBAL, "tag#2"),
                    new PlayerCharacter(2L, 987L, Region.EU, 2L, 1, "name#2"),
                    null,
                    null, null,  null,
                    false,
                    2, 1, 1, 1
                ),
                new LadderTeamMember
                (
                    new Account(2L, Partition.GLOBAL, "tag#1"),
                    new PlayerCharacter(1L, 2L, Region.EU, 2L, 2, "name#1"),
                    new Clan(2, "clan1", Region.EU, "clanName"),
                    1L, "proName1", "proTeam1",
                    null,
                    1, 2, 1, 1
                )
            ),
            null
        );

        InteractionCreateEvent evt = stubEmptyGuildEvent();
        assertEquals(expected, discordBootstrap.render(team, evt, 4));
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
        GuildRoleStore guildRoleStore = mock(GuildRoleStore.class);

        DiscordBootstrap.load
        (
            List.of(slashCommand),
            List.of(userCommand),
            List.of(),
            guildEmojiStore,
            guildRoleStore,
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

    @Test
    public void ifLongMessage_thenTrim()
    {
        StringBuilder sb = new StringBuilder(" ".repeat(DiscordBootstrap.MESSAGE_LENGTH_MAX + 1));
        assertTrue(DiscordBootstrap.trimIfLong(sb));
        assertEquals(DiscordBootstrap.MESSAGE_LENGTH_MAX, sb.length());
    }

    @Test
    public void ifLongString_thenTrim()
    {
        String longString = " ".repeat(DiscordBootstrap.MESSAGE_LENGTH_MAX + 1);
        assertEquals(DiscordBootstrap.MESSAGE_LENGTH_MAX, DiscordBootstrap.trimIfLong(longString).length());
    }

    @ValueSource(ints = {DiscordBootstrap.MESSAGE_LENGTH_MAX, DiscordBootstrap.MESSAGE_LENGTH_MAX - 1})
    @ParameterizedTest
    public void ifShortMessage_thenDoNothing(int length)
    {
        StringBuilder sb = new StringBuilder(" ".repeat(length));
        assertFalse(DiscordBootstrap.trimIfLong(sb));
        assertEquals(length, sb.length());
    }

    public static Stream<Arguments> testHaveSelfPermissions()
    {
        return Stream.of
        (
            Arguments.of
            (
                new Permission[]{Permission.ADD_REACTIONS, Permission.ATTACH_FILES},
                List.of(Permission.ADD_REACTIONS, Permission.ATTACH_FILES),
                true
            ),
            Arguments.of
            (
                new Permission[]{Permission.ADD_REACTIONS},
                List.of(Permission.ADD_REACTIONS, Permission.ATTACH_FILES),
                false
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testHaveSelfPermissions
    (
        Permission[] permissions,
        List<Permission> requiredPermissions,
        boolean expectedResult
    )
    {
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        when(member.getBasePermissions()).thenReturn(Mono.just(PermissionSet.of(permissions)));
        when(guild.getSelfMember()).thenReturn(Mono.just(member));

        assertEquals
        (
            expectedResult,
            DiscordBootstrap.haveSelfPermissions(guild, requiredPermissions).block()
        );
    }

    @Test
    public void whenNotInGuildContext_thenDontUseGuildStores()
    {
        Interaction interaction = mock(Interaction.class);
        when(interaction.getGuildId()).thenReturn(Optional.empty());
        ApplicationCommandInteractionEvent evt = mock(ApplicationCommandInteractionEvent.class);
        when(evt.getInteraction()).thenReturn(interaction);

        assertEquals(Race.TERRAN.getName(), discordBootstrap.getRaceEmojiOrName(evt, Race.TERRAN));
        assertEquals
        (
            BaseLeague.LeagueType.BRONZE.getName(),
            discordBootstrap.getLeagueEmojiOrName(evt, BaseLeague.LeagueType.BRONZE)
        );
        verifyNoInteractions(guildEmojiStore);
    }

    @CsvSource
    ({
        "tEst, `tEst`",
        "'tE st ', '`tE st `'",
        "*tEst*, `*tEst*`",
        "tE`st`, `tEst`"
    })
    @ParameterizedTest
    public void testSanitizeAndEscape(String in, String out)
    {
        assertEquals(out, DiscordBootstrap.sanitizeAndEscape(in));
    }

}
