// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.DiscordTestUtil;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionFollowupCreateMono;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Summary1v1SlashCommandTest
{

    @Mock
    private ConversionService conversionService;

    @Mock
    private LadderCharacterDAO ladderCharacterDAO;

    @Mock
    private PlayerCharacterSummaryDAO summaryDAO;

    @Mock
    private DiscordBootstrap discordBootstrap;

    @Mock
    private ChatInputInteractionEvent evt;

    @Mock
    private GatewayDiscordClient client;

    @Mock
    private InteractionFollowupCreateMono followup;

    @Captor
    private ArgumentCaptor<EmbedCreateSpec> embedCaptor;

    @Captor
    private ArgumentCaptor<OffsetDateTime> depthCaptor;

    @Test
    public void test()
    {
        stub();

        Summary1v1SlashCommand cmd =
            new Summary1v1SlashCommand(conversionService, ladderCharacterDAO, summaryDAO, discordBootstrap);
        cmd.handle(evt);

        verify(summaryDAO).find(any(), depthCaptor.capture());
        //verify correct depth, 10 seconds to run the test just in case
        assertTrue(OffsetDateTime.now().minusDays(100).toEpochSecond() - depthCaptor.getValue().toEpochSecond() < 10);

        verify(followup).withEmbeds(embedCaptor.capture());
        EmbedCreateSpec embed = embedCaptor.getValue();

        //verify output
        assertEquals("1v1 Summary", embed.title().get());
        StringBuilder sb = new StringBuilder();
        sb.append("*term, 100 days, Top 5, EU, Terran*\nGames | last/avg/max MMR\n\n");
        for(int i = 3; i > -1; i--)
        {
            sb.append(String.format("[**[proTeam%1$s]proName%1$s** | [clan%1$s]name | tag#%1$s | pro]" //unmasked name
                + "(https://www.nephest.com/sc2/?type=character&id=%1$s&m=1#player-stats-mmr)\n" //web link
                + DiscordBootstrap.REGION_EMOJIS.get(Region.EU) + " diamond terran" //region, league, race
                + " | **%1$s** | " //games
                + "**%1$s**/%2$s/%3$s\n\n", //last, avg, max mmr
                    i, i * 2, i * 3)); //stubbed values

        }
        assertEquals(sb.toString(), embed.description().get());
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

        List<LadderDistinctCharacter> characters = new ArrayList<>();
        for(int i = 0; i < 5; i++) characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#" + i, "name#" + i, "proName" + i, "clan" + i, "proTeam" + i, Region.EU,
            i, i
        ));
        //excluded by region filter
        characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#6", "name#6", "6", "clan6", "proTeam6", Region.US, 6, 6
        ));
        when(ladderCharacterDAO.findDistinctCharacters("term")).thenReturn(characters);

        List<PlayerCharacterSummary> summaries = new ArrayList<>();
        for(int i = 0; i < 4; i++) summaries.add(new PlayerCharacterSummary(
            (long) i, Race.TERRAN, i, i * 2, i * 3, i, BaseLeague.LeagueType.DIAMOND, i
        ));

        //excluded by race filter
        summaries.add(new PlayerCharacterSummary(
            4L, Race.ZERG, 4, 4, 4, 4, BaseLeague.LeagueType.DIAMOND, 4
        ));
        when(summaryDAO.find(eq(new Long[]{0L, 1L, 2L, 3L, 4L}), any())).thenReturn(summaries);
        when(discordBootstrap.embedBuilder()).thenReturn(EmbedCreateSpec.builder());
        when(discordBootstrap.getLeagueEmojiOrName(BaseLeague.LeagueType.DIAMOND)).thenReturn("diamond");
        when(discordBootstrap.getRaceEmojiOrName(Race.TERRAN)).thenReturn("terran");
        when(evt.createFollowup()).thenReturn(followup);
    }

}
