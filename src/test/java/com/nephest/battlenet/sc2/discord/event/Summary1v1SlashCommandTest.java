// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Summary1v1SlashCommandTest
{

    @Mock
    private ConversionService conversionService;

    @Mock
    private ChatInputInteractionEvent evt;

    @Mock
    private GatewayDiscordClient client;

    @Mock
    private Summary1v1Command cmdd;

    @Test
    public void test()
    {
        stub();

        Summary1v1SlashCommand cmd = new Summary1v1SlashCommand(cmdd, conversionService);
        cmd.handle(evt);
        verify(cmdd).handle(evt, "term", Region.EU, Race.TERRAN, 100);
    }

    private void stub()
    {
        when(evt.getOption("name")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("name")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("term")
                .build(),null)));

        when(evt.getOption("region")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("region")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("EU")
                .build(),null)));

        when(evt.getOption("race")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("race")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("Terran")
                .build(),null)));

        when(evt.getOption("depth")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("depth")
                .type(ApplicationCommandOption.Type.INTEGER.getValue())
                .value("100")
                .build(),null)));

        when(conversionService.convert("EU", Region.class)).thenReturn(Region.EU);
        when(conversionService.convert("Terran", Race.class)).thenReturn(Race.TERRAN);
    }

}
