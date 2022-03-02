// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "discord", name = "token")
public class Summary1v1UserCommand
implements UserCommand
{

    public static final Pattern USER_NAME_SANITIZER = Pattern.compile("[^\\p{L}\\p{N}]");
    private final Summary1v1Command summary1v1Command;

    @Autowired
    public Summary1v1UserCommand
    (
        Summary1v1Command summary1v1Command
    )
    {
        this.summary1v1Command = summary1v1Command;
    }

    @Override
    public ImmutableApplicationCommandRequest.Builder generateCommandRequest()
    {
        return ImmutableApplicationCommandRequest.builder()
            .name(Summary1v1Command.CMD_NAME)
            .type(ApplicationCommand.Type.USER.getValue());
    }

    @Override
    public String getCommandName()
    {
        return Summary1v1Command.CMD_NAME;
    }

    @Override
    public Mono<Message> handle(UserInteractionEvent evt)
    {
        String displayName = sanitizeName(DiscordBootstrap.getTargetDisplayNameOrName(evt).block());
        String name = sanitizeName(evt.getResolvedUser().getUsername());
        String[] names = displayName.equals(name)
            ? new String[]{displayName}
            : new String[]{displayName, name};
        return summary1v1Command.handle(evt, null, null, Summary1v1Command.DEFAULT_DEPTH, names);
    }

    public static String sanitizeName(String name)
    {
        String[] split = USER_NAME_SANITIZER.split(name.trim());
        return split[split.length - 1];
    }

}
