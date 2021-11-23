// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import reactor.core.publisher.Mono;

public interface SlashCommand
{

    ImmutableApplicationCommandRequest.Builder generateCommandRequest();

    String getCommandName();

    Mono<Message> handle(ChatInputInteractionEvent evt);

}
