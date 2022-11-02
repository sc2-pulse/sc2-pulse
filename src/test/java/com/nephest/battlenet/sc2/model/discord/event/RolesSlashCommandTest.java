// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.discord.event.RolesSlashCommand;
import com.nephest.battlenet.sc2.discord.event.Summary1v1Command;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.discord.GuildRoleStoreTest;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionFollowupCreateMono;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class RolesSlashCommandTest
{

    private static final String BOT_PAGE = "botPage";

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private LadderCharacterDAO ladderCharacterDAO;

    @Mock
    private PlayerCharacterSummaryDAO playerCharacterSummaryDAO;

    @Mock
    private GuildRoleStore guildRoleStore;

    @Mock
    private DiscordBootstrap discordBootstrap;

    @Mock
    private ChatInputInteractionEvent evt;

    @Mock
    private Interaction interaction;

    @Mock
    private Guild guild;

    @Mock
    private Member selfMember;

    @Mock
    private InteractionFollowupCreateMono followup;

    @Captor
    private ArgumentCaptor<String> responseCaptor;

    private RolesSlashCommand cmd;


    @BeforeEach
    public void beforeEach()
    {
        when(discordBootstrap.getDiscordBotPageUrl()).thenReturn(BOT_PAGE);
        cmd = new RolesSlashCommand
        (
            accountDAO,
            ladderCharacterDAO,
            playerCharacterSummaryDAO,
            guildRoleStore,
            discordBootstrap
        );
        stubInteraction();
    }

    private void stubInteraction()
    {
        when(evt.createFollowup()).thenReturn(followup);
        when(evt.getInteraction()).thenReturn(interaction);
        when(interaction.getGuild()).thenReturn(Mono.just(guild));
        when(guild.getSelfMember()).thenReturn(Mono.just(selfMember));
    }

    @Test
    public void verifyHandle()
    {
        //check permissions
        when(selfMember.getBasePermissions())
            .thenReturn(Mono.just(PermissionSet.of(Permission.EMBED_LINKS)));

        cmd.handle(evt).onErrorComplete().block();

        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        String expectedResponse = "Role management is disabled."
            + " Grant the bot \"MANAGE_ROLES\" permissions to enable it.";
        assertEquals(expectedResponse, responseCaptor.getValue());

        Permission[] permissions = RolesSlashCommand.REQUIRED_PERMISSIONS.toArray(Permission[]::new);
        when(selfMember.getBasePermissions()).thenReturn(Mono.just(PermissionSet.of(permissions)));

        //check mappings
        when(guildRoleStore.getRoleMappings(evt)).thenReturn(GuildRoleStore.EMPTY_MAPPING);

        cmd.handle(evt).onErrorComplete().block();

        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        expectedResponse = "[supported roles](<" + BOT_PAGE + "#slash-roles>) not found";
        assertEquals(expectedResponse, responseCaptor.getValue());

        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        long guildId = 657L;
        //tree map for predictable order
        TreeMap<Range<Integer>, List<Role>> ratingMap =
            new TreeMap<>(Comparator.comparing(Range::getMaximum));
        ratingMap.putAll((Map.of
        (
            Range.between(0, 10),
            List.of(new Role(client, GuildRoleStoreTest.roleData(7L, "1-11 MMR"), guildId)),

            Range.between(0, 99),
            List.of(new Role(client, GuildRoleStoreTest.roleData(8L, "1-100 MMR"), guildId)),

            Range.between(100, 999),
            List.of(new Role(client, GuildRoleStoreTest.roleData(9L, "100-1000 MMR"), guildId))
        )));
        //enum map for predictable order
        PulseMappings<Role> mappings = new PulseMappings<>
        (
            new EnumMap<>(Map.of
            (
                Region.US,
                List.of(new Role(client, GuildRoleStoreTest.roleData(1L, "US"), guildId)),
                Region.EU,
                List.of(new Role(client, GuildRoleStoreTest.roleData(2L, "europe"), guildId))
            )),
            new EnumMap<>(Map.of
            (
                BaseLeague.LeagueType.BRONZE,
                List.of
                (
                    new Role(client, GuildRoleStoreTest.roleData(3L, "bronze"), guildId),
                    new Role(client, GuildRoleStoreTest.roleData(4L, "metal"), guildId)
                ),
                BaseLeague.LeagueType.SILVER,
                List.of
                (
                    new Role(client, GuildRoleStoreTest.roleData(5L, "silver"), guildId),
                    new Role(client, GuildRoleStoreTest.roleData(4L, "metal"), guildId)
                )
            )),
            new EnumMap<>(Map.of
            (
                Race.TERRAN,
                List.of(new Role(client, GuildRoleStoreTest.roleData(6L, "terran"), guildId))
            )),
            ratingMap,
            Role::getMention,
            ", "
        );
        when(guildRoleStore.getRoleMappings(evt)).thenReturn(mappings);

        //check account
        long userId = 123L;
        User user = mock(User.class);
        when(user.getId()).thenReturn(Snowflake.of(userId));
        when(interaction.getUser()).thenReturn(user);
        when(accountDAO.findByDiscordUserId(userId)).thenReturn(Optional.empty());
        when(discordBootstrap.getAccountVerificationLink()).thenReturn("verificationLink");

        cmd.handle(evt).onErrorComplete().block();

        String rolesHeader = "[supported roles](<" + BOT_PAGE + "#slash-roles>)\n"
            + "**Region**: <@&1>, <@&2>\n"
            + "**League**: <@&3>, <@&4>, <@&5>\n"
            + "**Race**: <@&6>\n"
            + "**MMR**: <@&7>, <@&8>, <@&9>\n";
        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        expectedResponse = rolesHeader
            + "\n"
            + "verificationLink";
        assertEquals(expectedResponse, responseCaptor.getValue());

        Account account = new Account(987L, Partition.GLOBAL, "tag#1");
        when(accountDAO.findByDiscordUserId(userId)).thenReturn(Optional.of(account));

        //check characters
        when(ladderCharacterDAO.findDistinctCharacters(account.getBattleTag()))
            .thenReturn(List.of());
        when(discordBootstrap.getImportBattleNetDataLink()).thenReturn("importDataLink");

        cmd.handle(evt).onErrorComplete().block();

        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        expectedResponse = rolesHeader
            + "\n"
            + "Ranked 1v1 stats not found. Have you played a ranked game over the last 120 days? "
            + "If yes, then try to importDataLink to fix it.";
        assertEquals(expectedResponse, responseCaptor.getValue());

        List<LadderDistinctCharacter> characters = List.of
        (
            new LadderDistinctCharacter
            (
                BaseLeague.LeagueType.BRONZE,
                1,
                account,
                new PlayerCharacter(456L, account.getId(), Region.EU, 1L, 1, "name#1"),
                null, null, null, null,
                1, 1, 1, 1, 1,
                new LadderPlayerSearchStats(1, 1, 1),
                new LadderPlayerSearchStats(1, 1, 1)
            ),
            new LadderDistinctCharacter
            (
                BaseLeague.LeagueType.BRONZE,
                1,
                account,
                new PlayerCharacter(457L, account.getId(), Region.US, 1L, 1, "name#1"),
                null, null, null, null,
                1, 1, 1, 1, 1,
                new LadderPlayerSearchStats(1, 1, 1),
                new LadderPlayerSearchStats(1, 1, 1)
            )
        );
        when(ladderCharacterDAO.findDistinctCharacters(account.getBattleTag()))
            .thenReturn(characters);

        //check stats
        when(playerCharacterSummaryDAO.find
            (
                argThat(ids->{
                    if(ids == null) return true;
                    Arrays.sort(ids);
                    return Arrays.equals(ids, new Long[]{456L, 457L});
                }),
                argThat
                    (
                        odt->odt == null
                            || OffsetDateTime.now()
                            .minusDays(Summary1v1Command.DEFAULT_DEPTH)
                            .plusMinutes(1)
                            .isAfter(odt)
                    ),
                any()
            ))
            .thenReturn(List.of());

        cmd.handle(evt).onErrorComplete().block();

        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        expectedResponse = rolesHeader
            + "\n"
            + "Ranked 1v1 stats not found. Have you played a ranked game over the last 120 days? "
            + "If yes, then try to importDataLink to fix it.";
        assertEquals(expectedResponse, responseCaptor.getValue());

        List<PlayerCharacterSummary> summaries = List.of
        (
            new PlayerCharacterSummary
            (
                456L, Race.TERRAN, 123, 1, 1, 10,
                BaseLeague.LeagueType.BRONZE, 1
            ),
            new PlayerCharacterSummary
            (
                457L, Race.TERRAN, 1, 1, 1, 9,
                BaseLeague.LeagueType.SILVER, 1
            ),
            new PlayerCharacterSummary
            (
                457L, Race.ZERG, 1, 1, 1, 9,
                BaseLeague.LeagueType.SILVER, 1
            )
        );
        when(playerCharacterSummaryDAO.find
            (
                argThat(ids->{
                    if(ids == null) return true;
                    Arrays.sort(ids);
                    return Arrays.equals(ids, new Long[]{456L, 457L});
                }),
                argThat
                (
                    odt->odt == null
                        || OffsetDateTime.now()
                            .minusDays(Summary1v1Command.DEFAULT_DEPTH)
                            .plusMinutes(1)
                            .isAfter(odt)
                ),
                any())
            )
            .thenReturn(summaries);

        //check stats
        Member callerMember = mock(Member.class);
        when(interaction.getMember()).thenReturn(Optional.of(callerMember));
        Flux<Role> currentRoles = Flux.just
        (
            new Role(client, GuildRoleStoreTest.roleData(5L, "silver"), guildId),
            new Role(client, GuildRoleStoreTest.roleData(4L, "metal"), guildId),
            new Role(client, GuildRoleStoreTest.roleData(6L, "terran"), guildId),
            new Role(client, GuildRoleStoreTest.roleData(1L, "us"), guildId),
            new Role(client, GuildRoleStoreTest.roleData(9L, "100-1000 MMR"), guildId),
            new Role(client, GuildRoleStoreTest.roleData(10L, "notManagedRole"), guildId)
        );
        when(callerMember.getRoles()).thenReturn(currentRoles);
        when(callerMember.addRole(any(), anyString())).thenReturn(Mono.empty());
        when(callerMember.removeRole(any(), anyString())).thenReturn(Mono.empty());
        when(discordBootstrap.getLeagueEmojiOrName(evt, BaseLeague.LeagueType.BRONZE)).thenReturn("bronze");
        when(discordBootstrap.getRaceEmojiOrName(evt, Race.TERRAN)).thenReturn("terran");
        when(discordBootstrap.generateCharacterURL(any()))
            .thenAnswer(inv->"url" + inv.getArgument(0, LadderTeamMember.class).getCharacter().getId());

        cmd.handle(evt).onErrorComplete().block();

        verify(followup, atLeastOnce()).withContent(responseCaptor.capture());
        expectedResponse = rolesHeader
            + "\n"
            + "**1v1 Summary**\n"
            + "*tag#1, 120 days, Top 1*\n"
            + "**`Games`** | **last**/*avg*/max MMR\n"
            + "url456\n"
            + "\uD83C\uDDEA\uD83C\uDDFA bronze terran | **`123`** | **10**/*1*/1\n"
            + "\n"
            + "**Roles assigned**: <@&2>, <@&3>, <@&4>, <@&6>, <@&7>, <@&8>";
        assertEquals(expectedResponse, responseCaptor.getValue());
        ArgumentCaptor<Snowflake> addedRolesCaptor = ArgumentCaptor.forClass(Snowflake.class);
        ArgumentCaptor<Snowflake> removedRolesCaptor = ArgumentCaptor.forClass(Snowflake.class);
        verify(callerMember, times(4))
            .addRole(addedRolesCaptor.capture(), eq("/roles slash command"));
        List<Snowflake> addedRoles = addedRolesCaptor.getAllValues();
        assertEquals(4, addedRoles.size());
        addedRoles.sort(Comparator.comparing(Snowflake::asLong));
        assertEquals(2L, addedRoles.get(0).asLong());
        assertEquals(3L, addedRoles.get(1).asLong());
        assertEquals(7L, addedRoles.get(2).asLong());
        assertEquals(8L, addedRoles.get(3).asLong());

        verify(callerMember, times(3))
            .removeRole(removedRolesCaptor.capture(), eq("/roles slash command"));
        List<Snowflake> removedRoles = removedRolesCaptor.getAllValues();
        assertEquals(3, removedRoles.size());
        removedRoles.sort(Comparator.comparing(Snowflake::asLong));
        assertEquals(1L, removedRoles.get(0).asLong());
        assertEquals(5L, removedRoles.get(1).asLong());
        assertEquals(9L, removedRoles.get(2).asLong());

        verifyNoMoreInteractions(callerMember);
    }

}
