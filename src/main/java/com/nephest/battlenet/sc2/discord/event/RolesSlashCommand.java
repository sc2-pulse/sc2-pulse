// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Discord
@Component
public class RolesSlashCommand
implements SlashCommand
{

    public static final String CMD_NAME = "roles";
    public static final String DELIMITER = ", ";
    private final String supportedRolesLink;
    public static final List<Permission> REQUIRED_PERMISSIONS = List.of(Permission.MANAGE_ROLES);

    private final AccountDAO accountDAO;
    private final LadderCharacterDAO ladderCharacterDAO;
    private final PlayerCharacterSummaryDAO playerCharacterSummaryDAO;
    private final GuildRoleStore guildRoleStore;
    private final DiscordBootstrap discordBootstrap;

    @Autowired
    public RolesSlashCommand
    (
        AccountDAO accountDAO,
        LadderCharacterDAO ladderCharacterDAO,
        PlayerCharacterSummaryDAO playerCharacterSummaryDAO,
        GuildRoleStore guildRoleStore,
        DiscordBootstrap discordBootstrap
    )
    {
        this.accountDAO = accountDAO;
        this.ladderCharacterDAO = ladderCharacterDAO;
        this.playerCharacterSummaryDAO = playerCharacterSummaryDAO;
        this.guildRoleStore = guildRoleStore;
        this.discordBootstrap = discordBootstrap;
        supportedRolesLink =
            "[supported roles](<"
            + discordBootstrap.getDiscordBotPageUrl()
            + "#slash-roles>)";
    }

    @Override
    public ImmutableApplicationCommandRequest.Builder generateCommandRequest()
    {
        return ImmutableApplicationCommandRequest.builder()
            .name(CMD_NAME)
            .description("Get discord roles based on your ranked 1v1 stats")
            .defaultPermission(false)
            .dmPermission(false);
    }

    @Override
    public Mono<Message> handle(ChatInputInteractionEvent evt)
    {
        return DiscordBootstrap.haveSelfPermissions(evt.getInteraction().getGuild(), REQUIRED_PERMISSIONS)
            .flatMap(havePermissions->!havePermissions
                ? evt.createFollowup().withContent("Role management is disabled."
                    + " Grant the bot \""
                    + REQUIRED_PERMISSIONS.stream().map(Permission::name).collect(Collectors.joining(", "))
                    + "\" permissions to enable it.")
                : handleWithPermissions(evt)
            );
    }

    private Mono<Message> handleWithPermissions(ChatInputInteractionEvent evt)
    {
        PulseMappings<Role> mapping = guildRoleStore.getRoleMappings(evt);
        if(mapping.isEmpty()) return evt.createFollowup()
            .withContent(supportedRolesLink + " not found");

        StringBuilder response = appendHeader(new StringBuilder(), mapping).append("\n");

        Account account = accountDAO
            .findByDiscordUserId(evt.getInteraction().getUser().getId().asLong())
            .orElse(null);
        if(account == null)
        {
            response.append(discordBootstrap.getAccountVerificationLink());
            return evt.createFollowup().withContent(response.toString());
        }

        Map<Long, LadderTeamMember> characters = ladderCharacterDAO
            .findDistinctCharacters(account.getBattleTag())
            .stream()
            .collect(Collectors.toMap(c->c.getMembers().getCharacter().getId(), LadderDistinctCharacter::getMembers));
        if(characters.isEmpty())
            return evt.createFollowup()
                .withContent(appendStatsNotFoundMessage(response).toString());

        PlayerCharacterSummary topSummary = playerCharacterSummaryDAO
            .find
            (
                characters.keySet().toArray(Long[]::new),
                OffsetDateTime.now().minusDays(Summary1v1Command.DEFAULT_DEPTH),
                Race.EMPTY_RACE_ARRAY
            ).stream()
            .min(Summary1v1Command.DEFAULT_COMPARATOR)
            .orElse(null);
        if(topSummary == null)
            return evt.createFollowup()
                .withContent(appendStatsNotFoundMessage(response).toString());
        LadderTeamMember topMember = characters.get(topSummary.getPlayerCharacterId());
        Summary1v1Command.appendHeader
        (
            response,
            account.getBattleTag(),
            Summary1v1Command.DEFAULT_DEPTH,
            1,
            null, null, null
        ).append("\n");
        Summary1v1Command.appendSummary(response, topSummary, topMember, discordBootstrap, evt, 1)
            .append("\n\n");

        Member member = evt.getInteraction().getMember().orElseThrow();
        Set<Role> currentRoles = member.getRoles().toStream().collect(Collectors.toSet());

        Set<Role> assignedRoles = Stream.of
        (
            mapping.getRegionMappings().getMappings()
                .getOrDefault(topMember.getCharacter().getRegion(), List.of()).stream(),
            mapping.getLeagueMappings().getMappings()
                .getOrDefault(topSummary.getLeagueTypeLast(), List.of()).stream(),
            mapping.getRaceMappings().getMappings()
                .getOrDefault(topSummary.getRace(), List.of()).stream(),
            mapping.getRatingMappings().getMappings().entrySet().stream()
                .filter(e->e.getKey().contains(topSummary.getRatingLast()))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
        )
            .flatMap(Function.identity())
            .collect(Collectors.toSet());

        Set<Role> addedRoles = assignedRoles.stream()
            .filter(r->!currentRoles.contains(r))
            .collect(Collectors.toSet());

        Set<Role> removedRoles = currentRoles.stream()
            .filter(r->mapping.getValues().contains(r) && !assignedRoles.contains(r))
            .collect(Collectors.toSet());

        Stream<Mono<Void>> roleOperations = Stream.concat
        (
            removedRoles.stream().map(role->
                member.removeRole(role.getId(), "/" + CMD_NAME + " slash command")),
            addedRoles.stream().map(role->
                member.addRole(role.getId(), "/" + CMD_NAME + " slash command"))
        );
        Flux.fromStream(roleOperations)
            .flatMap(Function.identity())
            .blockLast();

        String assignedRolesString = assignedRoles.stream()
            .map(Role::getMention)
            .collect(Collectors.joining(DELIMITER));
        response.append("**Roles assigned**: ").append(assignedRolesString);

        DiscordBootstrap.trimIfLong(response);
        return evt.createFollowup().withContent(response.toString());
    }

    private StringBuilder appendStatsNotFoundMessage(StringBuilder sb)
    {
        return sb.append("Ranked 1v1 stats not found. Have you played a ranked game over the last ")
            .append(Summary1v1Command.DEFAULT_DEPTH)
            .append(" days? If yes, then try to ")
            .append(discordBootstrap.getImportBattleNetDataLink())
            .append(" to fix it.");
    }

    private StringBuilder appendHeader(StringBuilder sb, PulseMappings<Role> mapping)
    {
        sb.append(supportedRolesLink).append("\n");
        return appendMapping(sb, mapping);
    }

    public static StringBuilder appendMapping(StringBuilder sb, PulseMappings<Role> mapping)
    {
        if(!mapping.getRegionMappings().getMappings().isEmpty())
            sb.append("**Region**: ").append(mapping.getRegionMappings()).append("\n");
        if(!mapping.getLeagueMappings().getMappings().isEmpty())
            sb.append("**League**: ").append(mapping.getLeagueMappings()).append("\n");
        if(!mapping.getRaceMappings().getMappings().isEmpty())
            sb.append("**Race**: ").append(mapping.getRaceMappings()).append("\n");
        if(!mapping.getRatingMappings().getMappings().isEmpty())
            sb.append("**MMR**: ").append(mapping.getRatingMappings()).append("\n");

        return sb;
    }

    @Override
    public String getCommandName()
    {
        return CMD_NAME;
    }

    @Override
    public boolean isEphemeral()
    {
        return true;
    }

    @Override
    public boolean supportsMetaOptions()
    {
        return false;
    }

}
