// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.ApplicationCommandContexts;
import discord4j.core.object.command.ApplicationIntegrationType;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import java.util.List;
import java.util.stream.Stream;
import reactor.core.publisher.Mono;

public interface DiscordApplicationCommand<T extends ApplicationCommandInteractionEvent>
extends NamedCommand
{

    List<Integer> SUPPORTED_INTEGRATION_TYPES = Stream.of
    (
        ApplicationIntegrationType.GUILD_INSTALL,
        ApplicationIntegrationType.USER_INSTALL
    )
        .map(ApplicationIntegrationType::getValue)
        .toList();

    List<Integer> SUPPORTED_CONTEXTS = Stream.of
    (
        ApplicationCommandContexts.GUILD,
        ApplicationCommandContexts.PRIVATE_CHANNEL,
        ApplicationCommandContexts.BOT_DM
    )
        .map(ApplicationCommandContexts::getValue)
        .toList();

    ImmutableApplicationCommandRequest.Builder generateCommandRequest();

    Mono<Message> handle(T evt);

    boolean isEphemeral();

    boolean supportsMetaOptions();

}
