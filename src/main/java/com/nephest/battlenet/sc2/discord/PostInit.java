// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.discord.connection.ConnectionMetaData;
import com.nephest.battlenet.sc2.discord.connection.PulseConnectionParameters;
import com.nephest.battlenet.sc2.discord.event.AutoComplete;
import com.nephest.battlenet.sc2.discord.event.SlashCommand;
import com.nephest.battlenet.sc2.discord.event.UserCommand;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This component allows initialization of stuff that needs {@link DiscordAPI}. This workaround
 * is needed because DiscordAPI uses {@link SpringDiscordClient} for some operations, which makes
 * it unavailable in earlier stages of bean lifecycle due to circular dependencies.
 */
@Discord @Component
public class PostInit
{

    @Autowired
    public PostInit
    (
        List<SlashCommand> handlers,
        List<UserCommand> userInteractionHandlers,
        List<AutoComplete> autoCompleteHandlers,
        GuildEmojiStore guildEmojiStore,
        GuildRoleStore guildRoleStore,
        @Value("${discord.guild:}") Long guild,
        DiscordAPI discordAPI,
        PulseConnectionParameters connectionParameters
    )
    {
        DiscordBootstrap.load
        (
            handlers,
            userInteractionHandlers,
            autoCompleteHandlers,
            guildEmojiStore,
            guildRoleStore,
            discordAPI.getDiscordClient().getClient(),
            guild
        );
        init(discordAPI, connectionParameters);
    }

    private void init(DiscordAPI discordAPI, PulseConnectionParameters connectionParameters)
    {
        List<ConnectionMetaData> connectionMetaData = connectionParameters
            .getParameters()
            .values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        discordAPI.updateConnectionMetaData(connectionMetaData).block();
    }

}
