// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Discord
public class Summary1v1SlashCommand
implements SlashCommand
{

    private final Summary1v1Command summary1v1Command;
    private final ConversionService conversionService;

    @Autowired
    public Summary1v1SlashCommand
    (
        Summary1v1Command summary1v1Command,
        @Qualifier("mvcConversionService") ConversionService conversionService
    )
    {
        this.summary1v1Command = summary1v1Command;
        this.conversionService = conversionService;
    }

    @Override
    public ImmutableApplicationCommandRequest.Builder generateCommandRequest()
    {
        return ImmutableApplicationCommandRequest.builder()
            .name(Summary1v1Command.CMD_NAME)
            .description("League, games played; Last, avg, and max MMR; 1v1 only")
            .integrationTypes(DiscordApplicationCommand.SUPPORTED_INTEGRATION_TYPES)
            .contexts(DiscordApplicationCommand.SUPPORTED_CONTEXTS)
            .addOption(ApplicationCommandOptionData.builder()
                .name("name")
                .description("name, btag#123, [clantag], battlenet:://, starcraft2.blizzard.com.")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(true)
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
                .description("Depth in days")
                .type(ApplicationCommandOption.Type.INTEGER.getValue())
                .minValue(1.0)
                .maxValue((double) Summary1v1Command.MAX_LIMITED_DEPTH)
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
        Long depth = getDepth(evt);

        return summary1v1Command.handle(evt, region, race, depth, name);
    }

    private Long getDepth(ChatInputInteractionEvent evt)
    {
        Long depth = DiscordBootstrap
            .getArgument(evt, "depth", ApplicationCommandInteractionOptionValue::asLong, Summary1v1Command.MAX_DEPTH);
        if(depth == null) return depth;

        if(depth < 1 || depth > Summary1v1Command.MAX_LIMITED_DEPTH) depth = Summary1v1Command.MAX_DEPTH;
        return depth;
    }

    @Override
    public boolean supportsMetaOptions()
    {
        return true;
    }

}
