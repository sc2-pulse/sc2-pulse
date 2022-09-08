// Copyright (C) 2020-2022 Oleksandr Masniuk
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
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.EmojisUpdateEvent;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public static final boolean DEFAULT_EPHEMERAL = false;
    public static final int DEFAULT_LINES = 5;
    public static final int MESSAGE_LENGTH_MAX = 2000;
    public static final String SC2_GAME_NAME = "StarCraft II";
    public static final String SC2_REVEALED_TAG = "revealed";
    public static final String CHARACTER_URL_TEMPLATE =
        "https://www.nephest.com/sc2/?type=character&id=%1$s&m=1#player-stats-mmr";
    public static final String THUMBNAIL = "https://www.nephest.com/sc2/static/icon/misc/favicon-32.png";
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
    private final GuildEmojiStore guildEmojiStore;
    private final UpdateService updateService;

    @Autowired
    public DiscordBootstrap
    (
        @Value("#{${discord.race.emoji:{:}}}") Map<Race, String> raceEmojis,
        @Value("#{${discord.league.emoji:{:}}}") Map<BaseLeague.LeagueType, String> leagueEmojis,
        GuildEmojiStore guildEmojiStore,
        UpdateService updateService

    )
    {
        this.raceEmojis = raceEmojis;
        this.leagueEmojis = leagueEmojis;
        this.guildEmojiStore = guildEmojiStore;
        this.updateService = updateService;
    }

    public String getRaceEmojiOrName(InteractionCreateEvent evt, Race race)
    {
        return guildEmojiStore.getGuildRaceEmoji(guildEmojiStore.getGuildEmojis(evt), race)
            .orElse(raceEmojis.getOrDefault(race, race.getName()));
    }

    public String getLeagueEmojiOrName(InteractionCreateEvent evt, BaseLeague.LeagueType league)
    {
        return guildEmojiStore.getGuildLeagueEmoji(guildEmojiStore.getGuildEmojis(evt), league)
            .orElse(leagueEmojis.getOrDefault(league, league.getName()));
    }

    public static GatewayDiscordClient load
    (
        List<SlashCommand> handlers,
        List<UserCommand> userInteractionHandlers,
        List<AutoComplete> autoCompleteHandlers,
        GuildEmojiStore guildEmojiStore,
        String token,
        Long guild
    )
    {
        GatewayDiscordClient client = DiscordClientBuilder.create(token)
            .build()
            .login()
            .block();

        registerCommands(handlers, ChatInputInteractionEvent.class, ApplicationCommand.Type.CHAT_INPUT, client, guild, true);
        registerCommands(userInteractionHandlers, UserInteractionEvent.class, ApplicationCommand.Type.USER, client, guild, false);
        registerAutoCompleteHandlers(autoCompleteHandlers, client);
        client.on(ReconnectEvent.class, (e)->updatePresence(client));
        updatePresence(client).subscribe();
        client.on(EmojisUpdateEvent.class, guildEmojiStore::removeGuildEmojis).subscribe();
        return client;
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

    private static <T extends ApplicationCommandInteractionEvent> void registerCommands
    (
        List<? extends DiscordApplicationCommand<T>> handlers,
        Class<T> clazz,
        discord4j.core.object.command.ApplicationCommand.Type type,
        GatewayDiscordClient client,
        Long guild,
        boolean metaOptions
    )
    {
        List<ApplicationCommandRequest> reqs = handlers.stream()
            .map(c->metaOptions ? appendMetaOptions(c.generateCommandRequest()).build() : c.generateCommandRequest().build())
            .collect(Collectors.toList());
        registerCommands(client.getRestClient(), reqs, guild, type);

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
    (RestClient client, Collection<ApplicationCommandRequest> cmds, Long guild, ApplicationCommand.Type type)
    {
        Map<String, ApplicationCommandRequest> commands = cmds.stream()
            .collect(Collectors.toMap(ApplicationCommandRequest::name, Function.identity()));
        final ApplicationService applicationService = client.getApplicationService();
        final long applicationId = client.getApplicationId().block();

        //These are commands already registered with discord from previous runs of the bot.
        Map<String, ApplicationCommandData> discordCommands =
            getDiscordCommands(applicationService, applicationId, guild, type);

        for(ApplicationCommandRequest request : commands.values()){
            if (!discordCommands.containsKey(request.name()))
                addCommand(applicationService, request, applicationId, guild);
        }

        //Check if any commands have been deleted or changed.
        for (ApplicationCommandData discordCommand : discordCommands.values())
        {
            long discordCommandId = Long.parseLong(discordCommand.id());
            ApplicationCommandRequest command = commands.get(discordCommand.name());

            if (command == null)
            {
                //Removed, delete command
                removeCommand(applicationService, discordCommand, applicationId, guild, discordCommandId);
                continue; //Skip further processing on this command.
            }

            //Check if the command has been changed and needs to be updated.
            if (hasChanged(discordCommand, command))
                updateCommand(applicationService, command, applicationId, guild, discordCommandId);
        }
    }

    private static Map<String, ApplicationCommandData> getDiscordCommands
    (ApplicationService applicationService, long appId, Long guild, ApplicationCommand.Type type)
    {
        Flux<ApplicationCommandData> datas = guild == null
            ? applicationService.getGlobalApplicationCommands(appId)
            : applicationService.getGuildApplicationCommands(appId, guild);
        return datas
            .filter(c->c.type().toOptional().orElse(ApplicationCommand.Type.UNKNOWN.getValue()).equals(type.getValue()))
            .collectMap(ApplicationCommandData::name)
            .block();
    }


    private static void addCommand
    (ApplicationService applicationService, ApplicationCommandRequest req, long appId, Long guild)
    {
        if(guild == null)
        {
            applicationService.createGlobalApplicationCommand(appId, req).block();
            LOG.info("Created global command: {}", req.name());
        }
        else
        {
            applicationService.createGuildApplicationCommand(appId, guild, req).block();
            LOG.info("Created guild {} command: {}", guild, req.name());
        }
    }

    private static void removeCommand
    (ApplicationService applicationService, ApplicationCommandData cmd, long appId, Long guild, long cmdId)
    {
        if(guild == null)
        {
            applicationService.deleteGlobalApplicationCommand(appId, cmdId).block();
            LOG.info("Deleted global command: {}",  cmd.name());
        }
        else
        {
            applicationService.deleteGuildApplicationCommand(appId, guild, cmdId).block();
            LOG.info("Deleted guild {} command: {}", guild, cmd.name());
        }
    }

    private static void updateCommand
    (ApplicationService applicationService, ApplicationCommandRequest req, long appId, Long guild, long cmdId)
    {
        if(guild == null)
        {
            applicationService.modifyGlobalApplicationCommand(appId, cmdId, req).block();
            LOG.info("Updated global command: {}", req.name());
        }
        else
        {
            applicationService.modifyGuildApplicationCommand(appId, guild, cmdId, req).block();
            LOG.info("Updated guild {} command: {}", guild, req.name());
        }
    }

    private static boolean hasChanged(ApplicationCommandData discordCommand, ApplicationCommandRequest command)
    {
        // Compare types
        if (!discordCommand.type().toOptional().orElse(1).equals(command.type().toOptional().orElse(1))) return true;

        //Check if description has changed.
        if (!discordCommand.description().equals(command.description().toOptional().orElse(""))) return true;

        //Check if default permissions have changed
        boolean discordCommandDefaultPermission = discordCommand.defaultPermission().toOptional().orElse(true);
        boolean commandDefaultPermission = command.defaultPermission().toOptional().orElse(true);

        if (discordCommandDefaultPermission != commandDefaultPermission) return true;

        //Check and return if options have changed.
        return !discordCommand.options().equals(command.options());
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
        if(evt instanceof ChatInputInteractionEvent)
        {
            return getArgument((ChatInputInteractionEvent) evt, "ephemeral",
                ApplicationCommandInteractionOptionValue::asBoolean, DiscordBootstrap.DEFAULT_EPHEMERAL);
        }
        else
        {
            return cmd.isEphemeral();
        }
    }

    public static Mono<String> getTargetDisplayNameOrName(UserInteractionEvent evt)
    {
        Snowflake guildId = evt.getInteraction().getGuildId().orElse(null);
        return guildId != null
            ? evt.getResolvedUser().asMember(guildId).map(Member::getDisplayName)
            : Mono.just(evt.getResolvedUser().getUsername());
    }

    public static String generateCharacterURL(LadderTeamMember member)
    {
        return "[" + generateFullName(member, true) + "](<"
            + String.format(CHARACTER_URL_TEMPLATE, member.getCharacter().getId()) + ">)";
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

}
