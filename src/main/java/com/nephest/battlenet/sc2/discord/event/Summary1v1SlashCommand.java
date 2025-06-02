// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.web.service.SearchService;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Discord
public class Summary1v1SlashCommand
implements SlashCommand, AutoComplete
{

    private final Summary1v1Command summary1v1Command;
    private final ConversionService conversionService;
    private final SearchService searchService;

    @Autowired
    public Summary1v1SlashCommand
    (
        Summary1v1Command summary1v1Command,
        @Qualifier("mvcConversionService") ConversionService conversionService,
        SearchService searchService
    )
    {
        this.summary1v1Command = summary1v1Command;
        this.conversionService = conversionService;
        this.searchService = searchService;
    }

    @Override
    public ImmutableApplicationCommandRequest.Builder generateCommandRequest()
    {
        return ImmutableApplicationCommandRequest.builder()
            .name(Summary1v1Command.CMD_NAME)
            .description("League, games played; Last, avg, and max MMR; 1v1 only")
            .integrationTypes(DiscordApplicationCommand.SUPPORTED_INTEGRATION_TYPES)
            .addOption(ApplicationCommandOptionData.builder()
                .name("name")
                .description("name, btag#123, [clantag], battlenet:://, starcraft2.blizzard.com.")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(true)
                .autocomplete(true)
                .build())
            .addOption(ApplicationCommandOptionData.builder()
                .name("region")
                .description("Region filter")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .addAllChoices(DiscordBootstrap.generateChoices(conversionService, Region::getName, Region.values()))
                .build())
            .addOption(ApplicationCommandOptionData.builder()
                .name("race")
                .description("Race filter")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .addAllChoices(DiscordBootstrap.generateChoices(conversionService, Race::getName, Race.values()))
                .build())
            .addOption(ApplicationCommandOptionData.builder()
                .name("depth")
                .description("Depth in days. Default and max is 120 days. Unlimited for BattleTags.")
                .type(ApplicationCommandOption.Type.INTEGER.getValue())
                .minValue(1.0)
                .build());
    }

    @Override
    public String getCommandName()
    {
        return Summary1v1Command.CMD_NAME;
    }

    @Override
    public Mono<Message> handle(ChatInputInteractionEvent evt)
    {
        String name = DiscordBootstrap
            .getArgument(evt, "name", ApplicationCommandInteractionOptionValue::asString, null);
        Region region = DiscordBootstrap
            .getArgument(evt, "region", v->conversionService.convert(v.asString(), Region.class), null);
        Race race = DiscordBootstrap
            .getArgument(evt, "race", v->conversionService.convert(v.asString(), Race.class), null);
        LadderCharacterDAO.SearchType searchType = LadderCharacterDAO.SearchType.from(name);
        long depth = getDepth(evt, searchType);

        return summary1v1Command.handle(evt, region, race, depth, name);
    }

    private long getDepth(ChatInputInteractionEvent evt, LadderCharacterDAO.SearchType searchType)
    {
        long maxDepth = Summary1v1Command.MAX_DEPTH.getOrDefault(searchType, Summary1v1Command.DEFAULT_DEPTH);
        long depth = DiscordBootstrap
            .getArgument(evt, "depth", ApplicationCommandInteractionOptionValue::asLong, maxDepth);
        if(depth < 1 || depth > maxDepth) depth = maxDepth;
        return depth;
    }

    @Override
    public Iterable<ApplicationCommandOptionChoiceData> autoComplete(ChatInputAutoCompleteEvent evt)
    {
        if(!evt.getFocusedOption().getName().equals("name")) evt.respondWithSuggestions(List.of());

        String term = evt.getFocusedOption()
            .getValue()
            .map(ApplicationCommandInteractionOptionValue::asString)
            .orElse("");
        return searchService.suggestIfQuick(term, AutoComplete.DEFAULT_SUGGESTIONS_SIZE).stream()
            .map(s->ApplicationCommandOptionChoiceData.builder().name(s).value(s).build())
            .collect(Collectors.toList());
    }

    @Override
    public boolean supportsMetaOptions()
    {
        return true;
    }

}
