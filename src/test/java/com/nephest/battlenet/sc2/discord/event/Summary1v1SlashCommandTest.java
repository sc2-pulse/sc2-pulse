// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.SearchService;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

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

    @Mock
    private SearchService searchService;

    private Summary1v1SlashCommand cmd;

    @BeforeEach
    public void beforeEach()
    {
        cmd = new Summary1v1SlashCommand(cmdd, conversionService, searchService);
    }

    @Test
    public void test()
    {
        stub();
        cmd.handle(evt);
        verify(cmdd).handle(evt, Region.EU, Race.TERRAN, 100, "term");
    }

    private void stub()
    {
        when(evt.getOption("name")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("name")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("term")
                .build(), null, null)));

        when(evt.getOption("region")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("region")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("EU")
                .build(),null, null)));

        when(evt.getOption("race")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("race")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .value("Terran")
                .build(),null, null)));

        when(evt.getOption("depth")).thenReturn(Optional.of(new ApplicationCommandInteractionOption(client,
            ApplicationCommandInteractionOptionData.builder()
                .name("depth")
                .type(ApplicationCommandOption.Type.INTEGER.getValue())
                .value("100")
                .build(),null, null)));

        when(conversionService.convert("EU", Region.class)).thenReturn(Region.EU);
        when(conversionService.convert("Terran", Race.class)).thenReturn(Race.TERRAN);
    }

}
