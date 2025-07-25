// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.discord.event.AutoComplete;
import com.nephest.battlenet.sc2.discord.event.DiscordApplicationCommand;
import com.nephest.battlenet.sc2.discord.event.NamedCommand;
import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import com.nephest.battlenet.sc2.discord.event.UserCommand;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.EmojisUpdateEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.event.domain.role.RoleCreateEvent;
import discord4j.core.event.domain.role.RoleDeleteEvent;
import discord4j.core.event.domain.role.RoleUpdateEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.Role;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.service.ApplicationService;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DiscordBootstrap
{

    private static final Logger LOG = LoggerFactory.getLogger(DiscordBootstrap.class);

    public static final int DEFAULT_LINES = 5;
    public static final int MESSAGE_LENGTH_MAX = 2000;
    public static final String SC2_GAME_NAME = "StarCraft II";
    public static final String SC2_REVEALED_TAG = "revealed";
    public static final String UNEXPECTED_ERROR_MESSAGE =
        "Unexpected error occurred. Please report this bug, links are in the bot profile";
    public static final Color DEFAULT_COLOR = Color.of(0, 123, 255);
    public static final Map<Region, String> REGION_EMOJIS = Map.of
    (
        Region.US, "\uD83C\uDDFA\uD83C\uDDF8",
        Region.EU, "\uD83C\uDDEA\uD83C\uDDFA",
        Region.KR, "\uD83C\uDDF0\uD83C\uDDF7",
        Region.CN, "\uD83C\uDDE8\uD83C\uDDF3"
    );

    private final Map<Race, String> raceEmojis;
    private final Map<BaseLeague.LeagueType, String> leagueEmojis;
    private final String characterUrlTemplate;
    private final String accountVerificationLink;
    private final String importBattleNetDataLink;
    private final String discordBotPageUrl;
    private final DiscordAPI discordAPI;
    private final GuildEmojiStore guildEmojiStore;
    private final UpdateService updateService;

    @Autowired
    public DiscordBootstrap
    (
        @Value("#{${discord.race.emoji:{:}}}") Map<Race, String> raceEmojis,
        @Value("#{${discord.league.emoji:{:}}}") Map<BaseLeague.LeagueType, String> leagueEmojis,
        DiscordAPI discordAPI,
        GuildEmojiStore guildEmojiStore,
        UpdateService updateService,
        WebContextUtil webContextUtil
    )
    {
        this.discordAPI = discordAPI;
        this.raceEmojis = raceEmojis;
        this.leagueEmojis = leagueEmojis;
        characterUrlTemplate = webContextUtil.getCharacterUrlTemplate() + "#player-stats-mmr";
        this.accountVerificationLink =
            "[verify your account](<"
            + webContextUtil.getPublicUrl()
            + "verify/discord>)";
        this.importBattleNetDataLink =
            "[import your BattleNet profile](<"
            + webContextUtil.getPublicUrl()
            + "data/battle-net>)";
        this.discordBotPageUrl = webContextUtil.getPublicUrl() + "discord/bot";
        this.guildEmojiStore = guildEmojiStore;
        this.updateService = updateService;
    }

    public String getCharacterUrlTemplate()
    {
        return characterUrlTemplate;
    }

    private boolean isGuildAccessible(InteractionCreateEvent evt)
    {
        return evt.getInteraction().getGuildId()
            .map(id->discordAPI.getBotGuilds().containsKey(id))
            .orElse(false);
    }

    public String getRaceEmojiOrName(InteractionCreateEvent evt, Race race)
    {
        return isGuildAccessible(evt)
            ? guildEmojiStore.getGuildRaceEmoji(guildEmojiStore.getGuildEmojis(evt), race)
                .orElse(raceEmojis.getOrDefault(race, race.getName()))
            : raceEmojis.getOrDefault(race, race.getName());
    }

    public String getLeagueEmojiOrName(InteractionCreateEvent evt, BaseLeague.LeagueType league)
    {
        return isGuildAccessible(evt)
            ? guildEmojiStore.getGuildLeagueEmoji(guildEmojiStore.getGuildEmojis(evt), league)
                .orElse(leagueEmojis.getOrDefault(league, league.getName()))
            : leagueEmojis.getOrDefault(league, league.getName());
    }

    public static void load
    (
        List<SlashCommand> handlers,
        List<UserCommand> userInteractionHandlers,
        List<AutoComplete> autoCompleteHandlers,
        GuildEmojiStore guildEmojiStore,
        GuildRoleStore guildRoleStore,
        GatewayDiscordClient client,
        Long guild
    )
    {
        List<DiscordApplicationCommand<? extends ApplicationCommandInteractionEvent>> allHandlers =
            Stream.concat(handlers.stream(), userInteractionHandlers.stream())
                .collect(Collectors.toList());
        registerCommands(allHandlers, client, guild);
        registerHandlers(handlers, ChatInputInteractionEvent.class, client);
        registerHandlers(userInteractionHandlers, UserInteractionEvent.class, client);
        registerAutoCompleteHandlers(autoCompleteHandlers, client);
        client.on(ReconnectEvent.class, (e)->updatePresence(e.getClient())).subscribe();
        updatePresence(client).subscribe();
        client.on(EmojisUpdateEvent.class, guildEmojiStore::removeGuildEmojis).subscribe();
        client.on(RoleCreateEvent.class, guildRoleStore::removeRoles).subscribe();
        client.on(RoleUpdateEvent.class, guildRoleStore::removeRoles).subscribe();
        client.on(RoleDeleteEvent.class, guildRoleStore::removeRoles).subscribe();
        client.on(MemberUpdateEvent.class, guildRoleStore::removeRoles).subscribe();
    }

    private static Mono<Void> updatePresence(GatewayDiscordClient client)
    {
        return client.updatePresence(ClientPresence.online(ClientActivity.watching(SC2_GAME_NAME)));
    }

    private static void registerAutoCompleteHandlers
    (Collection<? extends AutoComplete> handlers, GatewayDiscordClient client)
    {
        Map<String, AutoComplete> handlerMap = handlers.stream()
            .collect(Collectors.toMap(NamedCommand::getCommandName, Function.identity()));
        client.on
        (
            ChatInputAutoCompleteEvent.class,
            e->e.respondWithSuggestions(handlerMap.get(e.getCommandName()).autoComplete(e))
        ).subscribe();
    }

    private static void registerCommands
    (
        Collection<? extends DiscordApplicationCommand<? extends ApplicationCommandInteractionEvent>> handlers,
        GatewayDiscordClient client,
        Long guild
    )
    {
        List<ApplicationCommandRequest> reqs = handlers.stream()
            .map(c->c.supportsMetaOptions() ? appendMetaOptions(c.generateCommandRequest()).build() : c.generateCommandRequest().build())
            .collect(Collectors.toList());
        registerCommands(client.getRestClient(), reqs, guild);
    }

    private static <T extends ApplicationCommandInteractionEvent> void registerHandlers
    (
        Collection<? extends DiscordApplicationCommand<T>> handlers,
        Class<T> clazz,
        GatewayDiscordClient client
    )
    {
        Map<String, DiscordApplicationCommand<T>> handlerMap = handlers.stream()
            .collect(Collectors.toMap(DiscordApplicationCommand::getCommandName, Function.identity()));
        client.on(clazz, evt->handle(handlerMap, evt)).subscribe();
    }

    private static <T extends ApplicationCommandInteractionEvent> Mono<Message> handle
    (Map<String, DiscordApplicationCommand<T>> handlerMap, T evt)
    {
        DiscordApplicationCommand<T> handler = handlerMap.get(evt.getCommandName());
        boolean ephemeral = getEphemeral(evt, handler);
        if(handler == null)
        {
            String msg = "Unsupported command: " + evt.getCommandName();
            LOG.error(msg);
            return evt.deferReply()
                .then(evt.createFollowup()
                    .withEphemeral(ephemeral)
                    .withContent(msg));
        }
        return evt.deferReply()
            .withEphemeral(ephemeral)
            .then(Mono.defer(()->handler.handle(evt)))
            .onErrorResume((t)->true, (t)->
            {
                LOG.error(t.getMessage(), t);
                /*
                    A client exception could happen because of the following reasons:
                        * discord is broken
                        * connection to discord is broken
                        * the discord lib is broken
                        * there is a duplicate bot online(when doing a seamless update for example)
                    All the reasons imply that you can't or shouldn't(seamless update) send a response.
                 */
                return t instanceof ClientException || ExceptionUtils.getRootCause(t) instanceof ClientException
                    ? Mono.empty()
                    : evt.createFollowup(UNEXPECTED_ERROR_MESSAGE);
            });
    }

    public static void registerCommands
    (RestClient client, List<ApplicationCommandRequest> cmds, Long guild)
    {
        final ApplicationService applicationService = client.getApplicationService();
        final long applicationId = client.getApplicationId().block();
        Flux<ApplicationCommandData> cmdOverride;
        if(guild != null)
        {
            cmdOverride = applicationService
                .bulkOverwriteGuildApplicationCommand(applicationId, guild, cmds);
        }
        else
        {
            cmdOverride = applicationService
                .bulkOverwriteGlobalApplicationCommand(applicationId, cmds);
        }
        cmdOverride.blockLast();
    }

    @SafeVarargs
    public static <T> List<ApplicationCommandOptionChoiceData> generateChoices
    (ConversionService conversionService, Function<? super T, ? extends String> nameGetter, T... vals)
    {
        return Arrays.stream(vals)
            .map(r->ApplicationCommandOptionChoiceData.builder()
                .name(nameGetter.apply(r))
                .value(conversionService.convert(r, String.class))
                .build())
            .collect(Collectors.toList());
    }

    public static <T> T getArgument
    (
        ChatInputInteractionEvent evt,
        String name,
        Function<? super ApplicationCommandInteractionOptionValue, T> getter,
        T def
    )
    {
        return evt.getOption(name)
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(getter)
            .orElse(def);
    }

    public static boolean getEphemeral(ApplicationCommandInteractionEvent evt, DiscordApplicationCommand<?> cmd)
    {
        return cmd.supportsMetaOptions() && evt instanceof ChatInputInteractionEvent
            ? getArgument((ChatInputInteractionEvent) evt, "ephemeral",
                ApplicationCommandInteractionOptionValue::asBoolean, cmd.isEphemeral())
            : cmd.isEphemeral();
    }

    public Mono<String> getTargetDisplayNameOrName(UserInteractionEvent evt)
    {
        return isGuildAccessible(evt)
            ? evt.getResolvedUser()
                .asMember(evt.getInteraction().getGuildId().orElseThrow())
                .map(Member::getDisplayName)
            : Mono.just(evt.getResolvedUser().getUsername());
    }

    public String generateCharacterURL(LadderTeamMember member)
    {
        return "[" + generateFullName(member, true) + "](<"
            + String.format(getCharacterUrlTemplate(), member.getCharacter().getId()) + ">)";
    }

    public String generateRaceCharacterURL(LadderTeamMember member, InteractionCreateEvent evt)
    {
        return getRaceEmojiOrName(evt, member.getFavoriteRace())
            + " " + generateCharacterURL(member);
    }

    public String render(LadderTeam team, InteractionCreateEvent evt, long gamesDigits)
    {
        String members = team.getMembers().stream()
            .map(m->generateRaceCharacterURL(m, evt))
            .collect(Collectors.joining(", "));
        return members + "\n"
            + REGION_EMOJIS.get(team.getRegion())
            + " " + getLeagueEmojiOrName(evt, team.getLeagueType())
            + " `"
            + String.format("%" + gamesDigits + "d", (team.getWins() + team.getLosses() + team.getTies()))
            + "` " + team.getRating();
    }

    public String getAccountVerificationLink()
    {
        return accountVerificationLink;
    }

    public String getImportBattleNetDataLink()
    {
        return importBattleNetDataLink;
    }

    public String getDiscordBotPageUrl()
    {
        return discordBotPageUrl;
    }

    public static String generateFullName(LadderTeamMember member, boolean boldName)
    {
        String additionalName = generateAdditionalName(member);
        StringBuilder sb = new StringBuilder();
        if(boldName) sb.append("**");
        sb
            .append(generateClanString(member))
            .append(generateName(member));
        if(boldName) sb.append("**");
        if(!additionalName.isBlank()) sb.append(" | ").append(additionalName);
        if(!Account.isFakeBattleTag(member.getAccount().getFakeOrRealBattleTag()))
            sb.append(" | ").append(member.getAccount().getFakeOrRealBattleTag());
        if(member.getProNickname() != null) sb.append(" | " + SC2_REVEALED_TAG);
        return sb.toString();
    }

    public static String generateClanString(LadderTeamMember member)
    {
        return member.getProTeam() != null
            ? "[" + member.getProTeam() + "]"
            : (member.getClan() != null ? "[" + member.getClan().getTag() + "]" : "");
    }

    public static String generateName(LadderTeamMember member)
    {
        return member.getProNickname() != null
            ? member.getProNickname()
            : member.getCharacter().getName().substring(0, member.getCharacter().getName().indexOf("#"));
    }

    public static String generateAdditionalName(LadderTeamMember member)
    {
        StringBuilder sb = new StringBuilder();
        String nameNoHash = member.getCharacter().getName().substring(0, member.getCharacter().getName().indexOf("#"));
        if(member.getClan() != null
            && member.getProTeam() != null
            && !Objects.equals(member.getProTeam(), member.getClan().getTag()))
            sb.append("[").append(member.getClan().getTag()).append("]");
        if(sb.length() > 0 || (member.getProNickname() != null && !Objects.equals(member.getProNickname(), nameNoHash)))
            sb.append(nameNoHash);
        return sb.toString();
    }

    public static String sanitizeAndEscape(String input)
    {
        return "`" + input.replaceAll("`", "") + "`";
    }

    public static Mono<Message> notFoundFollowup(ApplicationCommandInteractionEvent evt)
    {
        return evt.createFollowup("Not found. Try different filter combinations");
    }

    public EmbedCreateSpec.Builder embedBuilder()
    {
        return EmbedCreateSpec.builder()
            .timestamp(updateService.getUpdateContext(null).getExternalUpdate());
    }

    public static ImmutableApplicationCommandRequest.Builder appendMetaOptions
    (ImmutableApplicationCommandRequest.Builder builer)
    {
        return builer
            .addOption(ApplicationCommandOptionData.builder()
                .name("ephemeral")
                .description("Response is visible only to you")
                .type(ApplicationCommandOption.Type.BOOLEAN.getValue())
                .build());
    }

    public static boolean trimIfLong(StringBuilder sb)
    {
        if(sb.length() > DiscordBootstrap.MESSAGE_LENGTH_MAX)
        {
            sb.setLength(DiscordBootstrap.MESSAGE_LENGTH_MAX);
            return true;
        }
        return false;
    }

    public static String trimIfLong(String str)
    {
        return str.length() > DiscordBootstrap.MESSAGE_LENGTH_MAX
            ? str.substring(0, DiscordBootstrap.MESSAGE_LENGTH_MAX)
            : str;
    }

    public static Mono<PermissionSet> getSelfPermissions(Guild guild)
    {
        return guild
            .getSelfMember()
            .flatMap(PartialMember::getBasePermissions);
    }

    public static Mono<Boolean> haveSelfPermissions
    (
        Guild guild,
        Collection<? extends Permission> requiredPermissions
    )
    {
        return getSelfPermissions(guild)
            .map(p->p.containsAll(requiredPermissions));
    }

    public static Mono<Integer> getHighestRolePosition
    (
        Guild guild,
        Collection<? extends Permission> requiredPermissions
    )
    {
        return haveSelfPermissions(guild, requiredPermissions)
            .flatMap(havePermissions->havePermissions
                ? guild.getSelfMember()
                    .flatMap(PartialMember::getHighestRole)
                    .flatMap(Role::getPosition)
                : Mono.just(Integer.MIN_VALUE)
            );
    }

}
