// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.DiscordTestUtil;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.SearchService;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateMono;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Summary1v1CommandTest
{

    @Mock
    private PlayerCharacterSummaryDAO summaryDAO;

    @Mock
    private SearchService searchService;

    @Mock
    private DiscordBootstrap discordBootstrap;

    @Mock
    private ChatInputInteractionEvent evt;

    @Mock
    private InteractionFollowupCreateMono followup;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    @Captor
    private ArgumentCaptor<OffsetDateTime> depthCaptor;

    private Summary1v1Command cmd;

    @BeforeEach
    public void beforeEach()
    {
        when(evt.createFollowup()).thenReturn(followup);
        when(followup.withContent(anyString())).thenReturn(mock(InteractionFollowupCreateMono.class));
        cmd = new Summary1v1Command(summaryDAO, searchService, discordBootstrap);
    }

    @ValueSource(strings = {"term", "`term`", "te`rm"})
    @ParameterizedTest
    public void test(String term)
    {
        stub(term);

        cmd.handle
        (
            evt,
            "Additional description",
            Region.EU,
            Race.TERRAN,
            100,
            "emptyTerm", term
        );

        verify(summaryDAO).find(any(), depthCaptor.capture(), eq(Race.TERRAN));
        //verify correct depth, 10 seconds to run the test just in case
        assertTrue
        (
            SC2Pulse.offsetDateTime().minusDays(100).toEpochSecond()
            - depthCaptor.getValue().toEpochSecond()
            < 10
        );

        verify(followup).withContent(contentCaptor.capture());
        String content = contentCaptor.getValue();

        //verify output
        StringBuilder sb = new StringBuilder()
            .append("**1v1 Summary**\n")
            .append("Additional description\n")
            .append("`term`, *100 days, Top 5, EU, Terran*\n**`Games`** | **last**/*avg*/max MMR\n\n");
        for(int i = 3; i > -1; i--)
        {
            sb.append(String.format(
                "url%1$s\n" //web link
                    + DiscordBootstrap.REGION_EMOJIS.get(Region.EU) + " diamond terran" //region, league, race
                    + " | **`%4$s`** | " //games
                    + "**%1$s**/*%2$s*/%3$s\n\n", //last, avg, max mmr
                i, i * 2, i * 3, i == 3 ? "9999" : "   " + i)); //stubbed values

        }
        assertEquals(sb.toString(), content);
    }

    private void stub(String term)
    {
        List<LadderDistinctCharacter> characters = new ArrayList<>();
        for(int i = 0; i < 5; i++) characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#" + i, "name#" + i, (long) i, "proName" + i, "clan" + i, "proTeam" + i, Region.EU,
            i, i
        ));
        //excluded by region filter
        characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#6", "name#6", 6L, "6", "clan6", "proTeam6", Region.US, 6, 6
        ));
        when(searchService.findDistinctCharacters("emptyTerm")).thenReturn(List.of());
        when(searchService.findDistinctCharacters(term)).thenReturn(characters);

        List<PlayerCharacterSummary> summaries = new ArrayList<>();
        for(int i = 0; i < 4; i++)
            summaries.add
            (
                new PlayerCharacterSummary
                (
                    (long) i,
                    Race.TERRAN,
                    i == 3 ? 9999 : i,
                    i * 2,
                    i * 3,
                    i,
                    BaseLeague.LeagueType.DIAMOND,
                    i
                )
            );
        when(summaryDAO.find(eq(new Long[]{0L, 1L, 2L, 3L, 4L}), any(), eq(Race.TERRAN))).thenReturn(summaries);
        when(discordBootstrap.getLeagueEmojiOrName(evt, BaseLeague.LeagueType.DIAMOND)).thenReturn("diamond");
        when(discordBootstrap.getRaceEmojiOrName(evt, Race.TERRAN)).thenReturn("terran");
        when(discordBootstrap.generateCharacterURL(any()))
            .thenAnswer(inv->"url" + inv.getArgument(0, LadderTeamMember.class).getAccount().getId());
    }

    @Test
    public void whenNotFound_thenShowCustomMessage()
    {
        when(searchService.findDistinctCharacters(any())).thenReturn(List.of());

        cmd.handle
        (
            evt,
            "Additional description",
            Region.EU,
            Race.TERRAN,
            120,
            "name1", "name2"
        );
        verify(followup).withContent(contentCaptor.capture());
        String expectedResult =
            "**1v1 Summary**\n"
            + "Additional description\n"
            + "`name1, name2`, *120 days, Top 5, EU, Terran*\n"
            + "**`Games`** | **last**/*avg*/max MMR\n\n"

            + "**Not found**";
        assertEquals(expectedResult, contentCaptor.getValue());
    }

}
