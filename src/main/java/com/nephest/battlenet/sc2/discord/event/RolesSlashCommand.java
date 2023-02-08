// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Discord
@Component
public class RolesSlashCommand
implements SlashCommand
{

    public static final String CMD_NAME = "roles";
    public static final String DELIMITER = ", ";
    public static final String UPDATE_REASON = "Updated roles based on the last ranked ladder stats";
    private final String supportedRolesLink;
    public static final List<Permission> REQUIRED_PERMISSIONS = List.of(Permission.MANAGE_ROLES);

    private final AccountDAO accountDAO;
    private final GuildRoleStore guildRoleStore;
    private final DiscordBootstrap discordBootstrap;
    private final DiscordService discordService;

    @Autowired
    public RolesSlashCommand
    (
        AccountDAO accountDAO,
        GuildRoleStore guildRoleStore,
        DiscordBootstrap discordBootstrap,
        DiscordService discordService
    )
    {
        this.accountDAO = accountDAO;
        this.guildRoleStore = guildRoleStore;
        this.discordBootstrap = discordBootstrap;
        this.discordService = discordService;
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
            .dmPermission(false);
    }

    @Override
    public Mono<Message> handle(ChatInputInteractionEvent evt)
    {
        return evt.getInteraction().getGuild()
            .flatMap(guild->DiscordBootstrap.haveSelfPermissions(guild, REQUIRED_PERMISSIONS))
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
        PulseMappings<Role> mapping = guildRoleStore.getRoleMappings(evt).block();
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

        Tuple2<LadderTeam, LadderTeamMember> mainTuple = discordService
            .findMainTuple(account.getId());

        Triple<LadderTeam, Set<Role>, Flux<Void>> operations = updateRoles
        (
            mainTuple != null ? mainTuple.getT1() : null,
            mainTuple != null ? mainTuple.getT2().getFavoriteRace() : null,
            mapping,
            evt.getInteraction().getMember().orElseThrow()
        );
        operations.getRight().blockLast();

        if(mainTuple == null)
        {
            appendStatsNotFoundMessage(response);
        }
        else
        {
            appendMainTeamHeader(response, account.getBattleTag());
            LadderTeam mainTeam = mainTuple.getT1();
            int games = mainTeam.getWins() + mainTeam.getLosses() + mainTeam.getTies();
            response.append(discordBootstrap.render(mainTeam, evt, MiscUtil.stringLength(games)));
        }
        response.append("\n\n");
        String assignedRolesString = operations.getMiddle().stream()
            .map(Role::getMention)
            .collect(Collectors.joining(DELIMITER));
        response.append("**Roles assigned**: ").append(assignedRolesString);

        DiscordBootstrap.trimIfLong(response);
        return evt.createFollowup().withContent(response.toString());
    }

    public Triple<LadderTeam, Set<Role>, Flux<Void>> updateRoles
    (
        LadderTeam mainTeam,
        Race mainRace,
        PulseMappings<Role> mapping,
        Member member,
        String reason
    )
    {
        Set<Role> currentRoles = member.getRoles().toStream().collect(Collectors.toSet());

        Set<Role> assignedRoles = mainTeam == null ? Set.of() : Stream.of
        (
            mapping.getRegionMappings().getMappings()
                .getOrDefault(mainTeam.getRegion(), List.of()).stream(),
            mapping.getLeagueMappings().getMappings()
                .getOrDefault(mainTeam.getLeagueType(), List.of()).stream(),
            mapping.getRaceMappings().getMappings()
                .getOrDefault(mainRace, List.of()).stream(),
            mapping.getRatingMappings().getMappings().entrySet().stream()
                .filter(e->e.getKey().contains(mainTeam.getRating().intValue()))
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
                member.removeRole(role.getId(), reason)),
            addedRoles.stream().map(role->
                member.addRole(role.getId(), reason))
        );
        Flux<Void> operations =  Flux.fromStream(roleOperations)
            .flatMap(Function.identity());
        return new ImmutableTriple<>(mainTeam, assignedRoles, operations);
    }

    public Triple<LadderTeam, Set<Role>, Flux<Void>> updateRoles
    (
        LadderTeam mainTeam,
        Race mainRace,
        PulseMappings<Role> mapping,
        Member member
    )
    {
        return updateRoles(mainTeam, mainRace, mapping, member, UPDATE_REASON);
    }

    private StringBuilder appendStatsNotFoundMessage(StringBuilder sb)
    {
        return sb.append("Ranked 1v1 stats not found. Have you played a ranked game over the last ")
            .append(DiscordService.MAIN_TEAM_SEASON_DEPTH)
            .append(" seasons? If yes, then try to ")
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

    private StringBuilder appendMainTeamHeader(StringBuilder sb, String name)
    {
        sb.append("**Main team**\n")
            .append("*").append(name).append(", last ").append(DiscordService.MAIN_TEAM_SEASON_DEPTH)
                .append(" seasons*\n")
            .append("`Games` | MMR\n");
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
